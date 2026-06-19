# TASK-090 Hook: Shift Clip Start To The Action Peak

## Agent
worker-agent

## Summary
Short-form dies on the first second. A clip must open ON the action, not on the run-up to it. After a candidate window is chosen, shift the clip START toward the action peak (loudness/emotion/highlight peak inside the window) minus a small lead-in, instead of starting at the window edge with dead wind-up. Cheap, high payoff on top of detection.

## Context
See `Documentation/clip-quality-plan.md` (ladder #2). Candidate boundaries today are window-aligned (fixed 30s sliding windows in `analysis/service.py`), so a clip often opens on the approach to the moment, not the moment. A loudness profile and silence boundaries are already computed (`_window_peak_loudness`, silence segments) — the peak is available; we just do not use it to set the start.

## Problem Frame
- Symptom: clips open on the run-up; the hook is wasted.
- Suspected Layer: worker candidate-boundary logic (`worker/src/streamcut_worker/analysis/service.py` candidate construction).
- Touched Contracts: none (same `ClipCandidate` start/end fields; the start value is computed differently).
- Done Criterion: a candidate's start is shifted toward the in-window action peak minus a small lead-in, clamped to length bounds and snapped to a clean boundary (no mid-word cut).

## Scope
- When finalizing a candidate, compute the action peak within the window (use the existing loudness peak; if highlight detection from TASK-089 supplied a moment center/confidence, prefer that). Set the new start = peak - lead-in.
- Lead-in is a small configurable pre-roll (default ~0.4s) so the action is not cut off but the wind-up is removed.
- Snap the new start to the nearest word boundary (transcript) or silence boundary so the clip does not open mid-word/mid-syllable.
- Clamp so the clip respects min/max length bounds and never starts before 0 or after the moment.
- Keep the end boundary handling as-is (this task is start/hook-focused), unless trivially needed to preserve min length.

## Out of Scope
- No end-boundary rework / re-scoring.
- No backend/DB/API/transport/contract change.
- No export/subtitle/resize change.
- No dependency on TASK-089 being present — must work from loudness/silence alone, and merely *prefer* the highlight center when available.

## Inputs
- `worker/src/streamcut_worker/analysis/service.py` (`_select_candidates`, `ClipCandidate` construction, `_window_peak_loudness`)
- `worker/src/streamcut_worker/analysis/models.py`
- silence/transcript segment models for boundary snapping
- `worker/tests/test_candidate_analysis.py`

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- Candidate start shifts toward the in-window action peak minus the configurable lead-in.
- Start snaps to a nearby word/silence boundary (no mid-word open).
- Start is clamped within length bounds and valid range; existing candidate end/score behavior is preserved.
- Lead-in is configurable with a sensible default.
- Unit tests cover: start moves to peak-minus-lead-in, boundary snapping, and clamping at the edges.

## Constraints
- Reuse the existing loudness/silence/transcript signals; do not recompute audio.
- Keep `ClipCandidate` shape unchanged.
- No unrelated refactor of the scoring formula.

## Expected Deliverables
- code
- tests
