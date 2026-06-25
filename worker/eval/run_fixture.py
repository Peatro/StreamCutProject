"""Replay a frozen detector input through the detector, then score or dump it.

The frozen request is captured from a real job by running it once with
``STREAMCUT_FREEZE_ANALYSIS_DIR`` set (see
``streamcut_worker.analysis.fixture``).  Replaying it makes detector tuning
reproducible: the input never changes, so a hit-rate delta is the detector's,
not download/transcription jitter.

Usage:
    # Heuristic detector, score against hand labels:
    python -m eval.run_fixture --request fixtures/job-7.json \
        --labels eval/ground_truth/my-vod.json

    # Hybrid LLM path (needs a wired LlmClient; without one it falls back to
    # the heuristic, same as production):
    python -m eval.run_fixture --request fixtures/job-7.json \
        --labels eval/ground_truth/my-vod.json --hybrid

    # Just emit the detector candidates as JSON (no scoring):
    python -m eval.run_fixture --request fixtures/job-7.json --out candidates.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Sequence

# Make the worker package importable when run from the worker root without an
# editable install (mirrors the test bootstrap).
_WORKER_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_WORKER_ROOT))
sys.path.insert(0, str(_WORKER_ROOT / "src"))

from streamcut_worker.analysis.fixture import load_request

from eval.harness import Candidate, evaluate, load_labels, _print_report


def run_detector(request, *, hybrid: bool, llm_client=None) -> list[Candidate]:
    if hybrid:
        from streamcut_worker.analysis.hybrid import analyze_candidates_hybrid

        result = analyze_candidates_hybrid(request, llm_client)
    else:
        from streamcut_worker.analysis.service import analyze_candidates

        result = analyze_candidates(request)
    return [Candidate(start_sec=c.start_sec, end_sec=c.end_sec, score=c.score) for c in result.clip_candidates]


def main(argv: Sequence[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="Replay a frozen detector input and score/dump it.")
    parser.add_argument("--request", type=Path, required=True, help="Frozen CandidateAnalysisRequest JSON.")
    parser.add_argument("--labels", type=Path, help="Ground-truth labels JSON to score against.")
    parser.add_argument("--out", type=Path, help="Write detector candidates to this JSON file.")
    parser.add_argument("--hybrid", action="store_true", help="Use the hybrid LLM detector path.")
    parser.add_argument("--tolerance", type=float, default=0.0, help="Match slack in seconds (default 0).")
    args = parser.parse_args(argv)

    request = load_request(args.request)
    llm_client = None
    if args.hybrid:
        # Build the real Qwen client from env (QWEN_*). Only works where the
        # model + llama-cpp are present, i.e. inside the processing-worker.
        from streamcut_worker.inference import create_llm_client

        llm_client = create_llm_client()
        print(f"LLM client: enabled={llm_client.enabled} available={llm_client.is_available}")
    candidates = run_detector(request, hybrid=args.hybrid, llm_client=llm_client)
    print(f"Detector produced {len(candidates)} candidate(s).")

    if args.out:
        args.out.write_text(
            json.dumps(
                [{"start_sec": c.start_sec, "end_sec": c.end_sec, "score": c.score} for c in candidates],
                indent=2,
            ),
            encoding="utf-8",
        )
        print(f"Wrote candidates to {args.out}")

    if args.labels:
        result = evaluate(load_labels(args.labels), candidates, tolerance_sec=args.tolerance)
        print()
        _print_report(result)


if __name__ == "__main__":
    main()
