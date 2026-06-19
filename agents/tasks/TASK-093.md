# TASK-093 Vertical Reframe Tier 0 Blurred-Fill At Export

## Agent
worker-agent

## Summary
Reframe the exported clip to vertical 9:16 using a Tier 0 blurred-fill: the source scaled to fit the width on top of a blurred, zoomed copy of itself filling the background. A solved technique — implement it cleanly and do not over-engineer. This is plumbing, not the brain.

## Context
See `Documentation/clip-quality-plan.md` (ladder #4, "do it, do not spend brain"). No resize/reframe exists in the codebase today (export currently cuts the clip in its source aspect). Short-form platforms want 9:16. Tier 0 blurred-fill is the baseline, universally-acceptable reframe.

## Problem Frame
- Symptom: exported clips are in the source (typically 16:9) aspect, not vertical short-form.
- Suspected Layer: worker export (`worker/src/streamcut_worker/export/process.py` / `service.py`) ffmpeg filter graph.
- Touched Contracts: none (same export inputs/outputs; an added video filter stage).
- Done Criterion: the exported clip is 9:16 with the source centered and a blurred-fill background, within the export CPU budget.

## Scope
- Add a 9:16 blurred-fill reframe to the export ffmpeg graph: a blurred + cropped/zoomed background copy of the source filling a 1080x1920 (configurable) canvas, with the source scaled to fit the width and centered vertically on top.
- Make output dimensions, background blur strength, and the fit strategy configurable constants with sensible defaults.
- Toggle-able (enable flag); when disabled, export the clip in its source aspect as today.
- Respect the TASK-080 bounded `-threads` / two-stage-seek budget; blurred-fill adds a filter cost — measure and keep it within budget (blur on a downscaled background, not full-res, to stay cheap).
- Must compose correctly with TASK-092 subtitle burning if both are enabled (captions positioned for the 9:16 safe-zone — coordinate the safe-zone with TASK-092's positioning).

## Out of Scope
- No higher-tier reframe (face/subject tracking, smart crop, auto-zoom) — Tier 0 only. Explicitly do NOT build a tiering framework for one tier (no speculative abstraction).
- No subtitle work (TASK-092) beyond not conflicting with it.
- No detection/hook/transcription change.
- No backend/DB/API/transport change.

## Inputs
- `worker/src/streamcut_worker/export/process.py`, `export/service.py`, `export/models.py`
- TASK-080 export command (bounded threads, two-stage seek)
- `worker/tests/` export tests

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- Exported clip is 9:16 with the source centered over a blurred-fill background.
- Output size, blur strength, and fit are configurable with sensible defaults.
- Reframe is toggle-able; disabled exports the source aspect as before.
- Export stays within the TASK-080 CPU/threads budget (measure; blur the downscaled background, not full-res).
- Composes correctly with TASK-092 captions when both are enabled (safe-zone coordinated).
- Tests assert the ffmpeg filter graph is constructed for 9:16 blurred-fill (ffmpeg invocation mocked).

## Constraints
- Tier 0 only; no tiering framework / no speculative abstraction for future tiers.
- Respect the TASK-080 bounded-export budget; blur cheaply.
- Config constants tunable without code surgery.
- No unrelated refactor of the export path.

## Expected Deliverables
- code
- tests
