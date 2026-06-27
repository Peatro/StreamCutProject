"""Clip-worthiness AUC probe — a go/no-go diagnostic, NOT a ranker.

Scores each RAW candidate independently (one LLM call, full ±context transcript)
and measures whether that score separates hand-labeled true highlights from the
rest, via AUC over all true×other pairs. It answers one question: is there a
signal in the transcript that the detector's confidence does not capture?

Why this shape (vs recall@top-K on a deduped pool):
  * Run over the RAW pool, so the measurement isn't confounded by an upstream
    NMS/cut stage dropping a true positive before scoring.
  * AUC over ~G×(N-G) pairs is robust to one-clip noise; recall@K on G≈10 labels
    is a coarse 1-clip step that can't tell signal from chance on a single stream.
  * It also prints where the true positives land, so bimodality (some highlights
    clip-worthy by text, some purely visual/gameplay with no verbal hook) is
    visible — that's a modality ceiling, not a model failure.

Read: AUC≈0.5 → no signal, kill (same as comparative select-to-N). AUC≳0.7 →
real separation, worth hand-labeling 2-3 more streams and measuring recall@K
honestly. One fixture is a go/no-go read, never a verdict.

Usage (inside the processing-worker, with QWEN_* env for the model):
    python -m eval.probe_worthiness --request /app/eval/fixtures/21.json \
        --candidates /app/eval/exp_14b.json \
        --labels /app/eval/ground_truth/21.json \
        --context 60 --tolerance 20 --out /app/eval/worthiness_21.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

_WORKER_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_WORKER_ROOT))
sys.path.insert(0, str(_WORKER_ROOT / "src"))

from streamcut_worker.analysis.fixture import load_request

PROMPT = """You are judging whether a single moment from a livestream VOD would work as a standalone short-form clip (TikTok / Reels / Shorts).

A great clip has a HOOK that grabs attention in the first 1-2 seconds AND a PAYOFF — a punchline, a sharp take, a big reaction, genuine hype — and it lands on its own without the rest of the stream. Filler, slow setup with no payoff, or a mid-conversation fragment is weak.

Transcript around the moment (the candidate clip is roughly centered):
\"\"\"
{context}
\"\"\"

Rate from 0.00 (not clip-worthy) to 1.00 (excellent standalone short) how well THIS moment works as a clip.
Output ONLY the number.
SCORE:"""


def _context_text(segments, center: float, half_window: float) -> str:
    lo, hi = center - half_window, center + half_window
    parts = [
        s.text.strip()
        for s in segments
        if s.end_sec >= lo and s.start_sec <= hi and s.text.strip()
    ]
    return " ".join(" ".join(parts).split())


def _parse_score(raw: str) -> float | None:
    m = re.search(r"\d*\.?\d+", raw)
    if not m:
        return None
    try:
        v = float(m.group(0))
    except ValueError:
        return None
    if v > 1.0:  # model sometimes emits 0-100 or 0-10
        v = v / 100.0 if v > 10.0 else v / 10.0
    return max(0.0, min(1.0, v))


def _is_true(cand: dict, labels: list[dict], tol: float) -> bool:
    for lab in labels:
        lc = (lab["start_sec"] + lab["end_sec"]) / 2.0
        if (cand["start_sec"] - tol) <= lc <= (cand["end_sec"] + tol):
            return True
    return False


def _auc(true_scores: list[float], other_scores: list[float]) -> float:
    if not true_scores or not other_scores:
        return float("nan")
    wins = 0.0
    for t in true_scores:
        for o in other_scores:
            wins += 1.0 if t > o else (0.5 if t == o else 0.0)
    return wins / (len(true_scores) * len(other_scores))


def _selfcheck() -> None:
    assert _parse_score("0.8") == 0.8
    assert _parse_score("SCORE: 0.42 yes") == 0.42
    assert _parse_score("85") == 0.85          # 0-100 scale
    assert _parse_score("7") == 0.7            # 0-10 scale
    assert _parse_score("no number") is None
    assert _auc([0.9, 0.8], [0.1, 0.2]) == 1.0
    assert _auc([0.1], [0.9]) == 0.0
    assert _auc([0.5], [0.5]) == 0.5           # tie = 0.5
    labs = [{"start_sec": 100.0, "end_sec": 110.0}]
    assert _is_true({"start_sec": 90.0, "end_sec": 95.0}, labs, tol=20.0)   # 105 within [70,115]
    assert not _is_true({"start_sec": 0.0, "end_sec": 5.0}, labs, tol=20.0)
    print("selfcheck OK")


def main(argv=None) -> None:
    if argv is None and len(sys.argv) > 1 and sys.argv[1] == "--selfcheck":
        _selfcheck()
        return
    ap = argparse.ArgumentParser(description="Clip-worthiness AUC probe.")
    ap.add_argument("--request", type=Path, required=True, help="Frozen request (for transcript).")
    ap.add_argument("--candidates", type=Path, required=True, help="Raw candidate pool JSON.")
    ap.add_argument("--labels", type=Path, required=True, help="Ground-truth labels JSON.")
    ap.add_argument("--context", type=float, default=60.0, help="Half-window seconds of context each side.")
    ap.add_argument("--tolerance", type=float, default=20.0, help="True-positive match slack (matches harness).")
    ap.add_argument("--out", type=Path, help="Write per-candidate scores here.")
    args = ap.parse_args(argv)

    request = load_request(args.request)
    segments = request.transcript_segments
    cands = json.loads(args.candidates.read_text(encoding="utf-8"))
    labels = json.loads(args.labels.read_text(encoding="utf-8"))

    from streamcut_worker.inference import create_llm_client
    llm = create_llm_client()
    print(f"LLM client: enabled={llm.enabled} available={llm.is_available}")

    scored = []
    for i, c in enumerate(cands):
        center = (c["start_sec"] + c["end_sec"]) / 2.0
        ctx = _context_text(segments, center, args.context)
        raw = llm.generate(PROMPT.format(context=ctx), max_tokens=8, temperature=0.0)
        w = _parse_score(raw)
        scored.append({**c, "worthiness": w, "is_true": _is_true(c, labels, args.tolerance), "raw": raw.strip()[:40]})
        print(f"[{i+1}/{len(cands)}] {c['start_sec']:8.1f}s true={scored[-1]['is_true']} worthiness={w} raw={scored[-1]['raw']!r}")

    valid = [s for s in scored if s["worthiness"] is not None]
    true_s = sorted(s["worthiness"] for s in valid if s["is_true"])
    other_s = sorted(s["worthiness"] for s in valid if not s["is_true"])
    auc = _auc(true_s, other_s)

    print("\n" + "=" * 60)
    print(f"scored={len(valid)}/{len(scored)} (unparseable={len(scored)-len(valid)})")
    print(f"true positives n={len(true_s)} scores={true_s}")
    print(f"others n={len(other_s)} median={other_s[len(other_s)//2] if other_s else float('nan'):.3f} max={max(other_s) if other_s else float('nan'):.3f}")
    print(f"AUC(true>other) = {auc:.3f}")
    print("Read: ~0.5 no signal (kill) | >=~0.7 real separation (label more, measure recall@K honestly)")

    if args.out:
        args.out.write_text(json.dumps(scored, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
