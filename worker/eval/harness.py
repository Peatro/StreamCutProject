"""Offline evaluation harness for the highlight detector.

Compares detector candidates against hand-labeled ground-truth moments and
reports hit-rate (recall) and false-positive count.

Usage (CLI):
    # Score candidates from a JSON file against ground-truth labels:
    python -m eval.harness \
        --labels eval/ground_truth/msc-2026-06-15.json \
        --candidates candidates.json

    # With a custom tolerance (seconds added to each side of a candidate):
    python -m eval.harness \
        --labels eval/ground_truth/msc-2026-06-15.json \
        --candidates candidates.json \
        --tolerance 5.0

Candidate JSON format (same shape as ClipCandidate):
    [{"start_sec": float, "end_sec": float, "score": float}, ...]

Ground-truth JSON format:
    [{"source": str, "start_sec": float, "end_sec": float, "note": str}, ...]
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


# ---------------------------------------------------------------------------
# Data types (thin; reuse runtime ClipCandidate shape but accept plain dicts)
# ---------------------------------------------------------------------------

@dataclass(frozen=True, slots=True)
class Label:
    """A single hand-labeled ground-truth moment."""

    source: str
    start_sec: float
    end_sec: float
    note: str

    @property
    def center_sec(self) -> float:
        return (self.start_sec + self.end_sec) / 2.0


@dataclass(frozen=True, slots=True)
class Candidate:
    """A detector candidate (mirrors ClipCandidate's temporal fields)."""

    start_sec: float
    end_sec: float
    score: float


# ---------------------------------------------------------------------------
# Matching logic
# ---------------------------------------------------------------------------

def _label_is_hit(
    label: Label,
    candidates: Sequence[Candidate],
    tolerance_sec: float = 0.0,
) -> bool:
    """Return True if the label's center falls inside any candidate's range.

    The matching rule is **label-center-inside-candidate**: the midpoint of the
    labeled interval must lie within ``[candidate.start_sec - tolerance,
    candidate.end_sec + tolerance]`` for at least one candidate.

    This is preferred over IoU because labels and candidates often have very
    different durations (e.g. a 5-second label vs. a 30-second window),
    making IoU misleadingly low even when the detector clearly found the
    right moment.
    """
    center = label.center_sec
    for c in candidates:
        if (c.start_sec - tolerance_sec) <= center <= (c.end_sec + tolerance_sec):
            return True
    return False


def _candidate_is_false_positive(
    candidate: Candidate,
    labels: Sequence[Label],
    tolerance_sec: float = 0.0,
) -> bool:
    """Return True if no label's center falls inside this candidate's range."""
    for label in labels:
        if (candidate.start_sec - tolerance_sec) <= label.center_sec <= (candidate.end_sec + tolerance_sec):
            return False
    return True


# ---------------------------------------------------------------------------
# Evaluation result
# ---------------------------------------------------------------------------

@dataclass(frozen=True, slots=True)
class LabelResult:
    """Per-label hit/miss detail."""

    label: Label
    hit: bool


@dataclass(frozen=True, slots=True)
class EvalResult:
    """Aggregate evaluation metrics."""

    hit_rate: float
    hits: int
    misses: int
    false_positives: int
    total_labels: int
    total_candidates: int
    per_label: list[LabelResult]


def evaluate(
    labels: Sequence[Label],
    candidates: Sequence[Candidate],
    tolerance_sec: float = 0.0,
) -> EvalResult:
    """Score candidates against labels and return metrics.

    Args:
        labels: Hand-labeled ground-truth moments.
        candidates: Detector output candidates.
        tolerance_sec: Seconds of slack added to each side of a candidate
            when checking if a label center falls inside it.  Default 0.

    Returns:
        An ``EvalResult`` with hit-rate, false-positive count, and per-label
        breakdown.
    """
    per_label: list[LabelResult] = []
    hits = 0

    for label in labels:
        is_hit = _label_is_hit(label, candidates, tolerance_sec)
        per_label.append(LabelResult(label=label, hit=is_hit))
        if is_hit:
            hits += 1

    misses = len(labels) - hits
    false_positives = sum(
        1 for c in candidates if _candidate_is_false_positive(c, labels, tolerance_sec)
    )

    return EvalResult(
        hit_rate=hits / len(labels) if labels else 0.0,
        hits=hits,
        misses=misses,
        false_positives=false_positives,
        total_labels=len(labels),
        total_candidates=len(candidates),
        per_label=per_label,
    )


# ---------------------------------------------------------------------------
# JSON I/O helpers
# ---------------------------------------------------------------------------

def load_labels(path: Path) -> list[Label]:
    """Load ground-truth labels from a JSON file."""
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    return [
        Label(
            source=entry["source"],
            start_sec=float(entry["start_sec"]),
            end_sec=float(entry["end_sec"]),
            note=entry.get("note", ""),
        )
        for entry in raw
    ]


def load_candidates(path: Path) -> list[Candidate]:
    """Load detector candidates from a JSON file.

    Accepts the same shape as ``ClipCandidate.to_payload()`` (camelCase) or
    the dataclass field names (snake_case).
    """
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    return [
        Candidate(
            start_sec=float(entry.get("start_sec", entry.get("startSec", 0.0))),
            end_sec=float(entry.get("end_sec", entry.get("endSec", 0.0))),
            score=float(entry.get("score", 0.0)),
        )
        for entry in raw
    ]


# ---------------------------------------------------------------------------
# Adapter: run the existing detector and emit Candidate objects
# ---------------------------------------------------------------------------

def candidates_from_detector(
    *,
    job_id: str,
    transcript_segments: list,
    silence_segments: list,
    duration_sec: float,
    emotion_keywords: tuple[str, ...] = (),
    loudness_profile=None,
    window_duration_sec: float = 30.0,
    step_sec: float = 5.0,
    top_n: int | None = None,
    min_overlap_ratio: float = 0.0,
) -> list[Candidate]:
    """Run the existing sliding-window detector and return Candidate objects.

    This is the "live adapter" path: it reuses the real analysis service so
    the harness can score whatever the current detector produces.  All
    parameters mirror ``CandidateAnalysisRequest``.
    """
    # Import here to keep the eval module usable without the full worker
    # dependency tree installed (the JSON-only path works standalone).
    from streamcut_worker.analysis.models import CandidateAnalysisRequest
    from streamcut_worker.analysis.service import analyze_candidates

    request = CandidateAnalysisRequest(
        job_id=job_id,
        transcript_segments=transcript_segments,
        silence_segments=silence_segments,
        duration_sec=duration_sec,
        emotion_keywords=emotion_keywords,
        loudness_profile=loudness_profile,
        window_duration_sec=window_duration_sec,
        step_sec=step_sec,
        top_n=top_n,
        min_overlap_ratio=min_overlap_ratio,
    )
    result = analyze_candidates(request)
    return [
        Candidate(
            start_sec=c.start_sec,
            end_sec=c.end_sec,
            score=c.score,
        )
        for c in result.clip_candidates
    ]


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _print_report(result: EvalResult) -> None:
    print(f"Hit-rate:         {result.hit_rate:.2%}  ({result.hits}/{result.total_labels})")
    print(f"Misses:           {result.misses}")
    print(f"False positives:  {result.false_positives}  (of {result.total_candidates} candidates)")
    print()
    print("Per-label breakdown:")
    for lr in result.per_label:
        status = "HIT " if lr.hit else "MISS"
        print(f"  [{status}] {lr.label.start_sec:8.1f}s - {lr.label.end_sec:8.1f}s  {lr.label.note}")


def main(argv: Sequence[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate detector candidates against ground-truth labels.",
    )
    parser.add_argument(
        "--labels",
        type=Path,
        required=True,
        help="Path to ground-truth JSON label file.",
    )
    parser.add_argument(
        "--candidates",
        type=Path,
        required=True,
        help="Path to detector candidates JSON file.",
    )
    parser.add_argument(
        "--tolerance",
        type=float,
        default=0.0,
        help="Seconds of slack added to each side of a candidate for matching (default: 0).",
    )
    args = parser.parse_args(argv)

    labels = load_labels(args.labels)
    candidates = load_candidates(args.candidates)
    result = evaluate(labels, candidates, tolerance_sec=args.tolerance)
    _print_report(result)


if __name__ == "__main__":
    main()
