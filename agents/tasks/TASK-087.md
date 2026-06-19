# TASK-087 Ground-Truth Clip Set And Detector Evaluation Harness

## Agent
worker-agent

## Summary
Before the highlight detector is tuned, build a small hand-labeled ground-truth format and an offline evaluation harness that measures how well the candidate detector's picks overlap the operator's hand-picked moments. This makes "the detection got better" a measured number (hit-rate), not a feeling. Do FIRST — it gates TASK-089 tuning.

## Context
See `Documentation/clip-quality-plan.md`. Clip-selection quality is the product's brain. Tuning thresholds without a labeled reference is blind. The operator has years of stream data and knows what landed; the honest signal is the detector's hit-rate against a hand-picked set. The current detector is the heuristic in `worker/src/streamcut_worker/analysis/service.py` (sliding window). This task does NOT change the detector — it builds the measuring stick.

## Problem Frame
- Symptom: no way to objectively tell if a detector change is an improvement.
- Suspected Layer: new offline evaluation tooling under `worker/eval/` (no runtime/pipeline change).
- Touched Contracts: none.
- Done Criterion: given a ground-truth label file and a detector's candidate output for the same source, the harness prints a hit-rate (recall against labels) and a false-positive count, deterministically.

## Scope
- Define a ground-truth file format: JSON list `[{ "source": str, "start_sec": float, "end_sec": float, "note": str }]` stored under `worker/eval/ground_truth/`. Include a short README describing how the operator hand-labels moments. Seed with one example file (clearly marked as a sample, not real labels) so the format is concrete.
- Build an evaluation harness (a small Python module + CLI under `worker/eval/`) that:
  - takes a ground-truth file and a set of detector candidates (`ClipCandidate`-shaped: start_sec/end_sec/score) for the same source,
  - matches each labeled moment to candidates by temporal overlap (a label is "hit" if some candidate's overlap with it passes a configurable threshold — IoU or label-center-inside-candidate; pick one, document it),
  - reports: hit-rate (labels hit / total labels), false-positive count (candidates matching no label), and a per-label breakdown.
- Provide a way to feed it real detector output: a thin adapter that runs the existing `analyze_candidates` over a source's transcript/silence/loudness inputs (reuse existing models) and emits candidates the harness can score. If wiring full real inputs is heavy, accept candidates from a JSON file too, and document both paths.

## Out of Scope
- No change to the detector, scoring, or pipeline runtime.
- No backend/DB/API/transport change.
- No automated CI gate (this is an operator-run offline tool); just make it runnable.
- Do not invent labels — ship only a clearly-marked sample; the operator populates real ones.

## Inputs
- `worker/src/streamcut_worker/analysis/service.py` (`analyze_candidates`, `ClipCandidate`, `CandidateAnalysisRequest`)
- `worker/src/streamcut_worker/analysis/models.py`
- `worker/tests/test_candidate_analysis.py` (test patterns)

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- A documented ground-truth JSON format exists under `worker/eval/ground_truth/` with a sample file and README.
- The harness computes hit-rate and false-positive count deterministically from labels + candidates.
- The matching threshold is configurable and its definition (IoU vs center-inside) is documented.
- At least one runnable path produces real candidates from the existing detector for scoring.
- One small unit test asserts the metric on a tiny hand-crafted labels+candidates fixture (e.g. 1 hit, 1 miss, 1 false positive).

## Constraints
- Offline tooling only; no new always-on dependency in the worker runtime.
- Reuse existing analysis models/types; do not duplicate them.
- Keep it minimal — this is a measuring stick, not a framework.

## Expected Deliverables
- code
- tests
