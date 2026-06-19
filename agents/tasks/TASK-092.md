# TASK-092 Word-By-Word Karaoke Burned-In Subtitles At Export

## Agent
worker-agent

## Summary
Burn word-by-word karaoke captions into the exported clip: readable on mute, words highlighted in time with speech, positioned above the platform UI safe-zone. Uses the per-word timings persisted by TASK-091, rendered as styled ASS and muxed by ffmpeg during export. Keep within the bounded-export CPU budget from TASK-080.

## Context
See `Documentation/clip-quality-plan.md` (ladder #3; decision: word-by-word karaoke). Export runs in the export-worker (`worker/src/streamcut_worker/export/`). The clip's word timings come from TASK-091 (persisted; do not re-transcribe). The bottom of the frame is covered by platform chrome, so captions sit above the safe-zone.

## Problem Frame
- Symptom: exported clips have no captions; muted viewers get nothing.
- Suspected Layer: worker export (`worker/src/streamcut_worker/export/process.py` / `service.py`) + an ASS subtitle generator.
- Touched Contracts: none (same export inputs/outputs; captions are an additional render step). Reads the word timings TASK-091 made available.
- Done Criterion: the exported clip has burned-in word-by-word karaoke captions, time-aligned to the clip, above the safe-zone, produced within the export CPU budget.

## Scope
- Generate an ASS subtitle file for the clip from the clip-local word timings (offset words to clip-relative time; only words inside [clip start, clip end]). Use ASS karaoke timing (`\k`) so the active word/segment highlights in sync.
- Style for short-form: large, high-contrast, readable on mute; positioned above the platform UI safe-zone (not flush to the bottom). Make font/size/position/colors configurable constants (tunable without code surgery), with sensible defaults.
- Burn into the export with ffmpeg (libass `subtitles=` / `ass=` filter) as part of the existing export command. Respect the TASK-080 bounded `-threads` / two-stage-seek approach; measure and keep export time/CPU within that budget. Make subtitle burning toggle-able (enable flag) and a no-op when word timings are absent (graceful: export the clip without captions rather than failing).
- Group words into readable on-screen lines/phrases (a few words at a time), not one word alone on screen, while still highlighting the current word.

## Out of Scope
- No transcription/word-timing persistence (that is TASK-091; this task consumes it).
- No re-transcription at export.
- No resize/reframe (that is TASK-093) — though it must not conflict with it.
- No backend/DB/API/transport change.
- No detection/hook change.

## Inputs
- `worker/src/streamcut_worker/export/process.py`, `export/service.py`, `export/models.py`
- the persisted word timings from TASK-091 (how the export-worker receives the clip's transcript/words)
- TASK-080 export command (bounded threads, two-stage seek) — keep its budget
- `worker/tests/` export tests

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- Exported clip has burned-in word-by-word karaoke captions, time-aligned to the clip and highlighting the current word.
- Captions sit above the platform UI safe-zone; style is readable on mute and configurable.
- Subtitle burning is toggle-able and degrades gracefully to a caption-less export when word timings are missing.
- Export stays within the TASK-080 CPU/threads budget (measure and report).
- Tests cover ASS generation from word timings (clip-relative offset, phrase grouping, karaoke timing) with ffmpeg invocation mocked.

## Constraints
- Consume TASK-091 word timings; do not re-transcribe.
- Respect TASK-080 bounded-export budget; no CPU-hog regression.
- Style constants tunable without code surgery.
- No unrelated refactor of the export path.

## Expected Deliverables
- code
- tests
