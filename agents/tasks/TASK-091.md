# TASK-091 Persist Word-Level Transcript Timings End-To-End

## Agent
backend-agent

## Summary
faster-whisper already computes per-word timestamps (`word_timestamps=True`), but the worker throws them away and keeps only `word_count`. Persist the per-word timings through the worker transcript model, the worker→backend transport payload, and backend storage, so the export stage can build word-by-word karaoke subtitles (TASK-092) without re-transcribing the clip.

## Context
See `Documentation/clip-quality-plan.md` (decision: stop discarding word timings). `worker/src/streamcut_worker/transcription/service.py` requests `word_timestamps=True` and the words exist on each segment, but `TranscriptSegment`/`to_payload()` reduce them to a count. Karaoke subtitles (TASK-092) run in the export-worker, a separate task claimed after moderation — the analysis-time word timings are gone by then unless persisted.

## Problem Frame
- Symptom: per-word timings are computed then discarded; export cannot align captions to words.
- Suspected Layer: worker transcript model + transport payload + backend transcript persistence/schema.
- Touched Contracts: worker transport (transcript payload) and the transcript data model/schema.
- Done Criterion: per-word `(word, start_sec, end_sec)` triples are produced by the worker, sent in the transcript payload, stored, and retrievable for a job's transcript.

## Scope
- Worker: extend `TranscriptSegment` (and `to_payload`) to carry the per-word list `[{word, startSec, endSec}]` from the already-available whisper word data. Keep `word_count` for compatibility.
- Transport: add the words to the transcript ingestion payload (`agents/contracts/worker-protocol.md` transcript shape) — additive, optional, backward-compatible.
- Backend: persist words (a `transcript_word` child table keyed to the transcript segment, OR a JSON column on the segment — choose per the existing persistence style and justify). Add the Liquibase migration. Expose words where the transcript is served (additive field), so the export path / API can read them.
- Keep it additive and backward-compatible: a transcript without words must still work (older data, or words disabled).

## Out of Scope
- No subtitle rendering/burning (that is TASK-092) — this task only makes the data available.
- No change to analysis/scoring/silence/loudness/export logic.
- No UI change (the review UI need not display words).
- No change to non-transcript contracts.

## Inputs
- `worker/src/streamcut_worker/transcription/models.py`, `transcription/service.py` (words already on the whisper segment)
- worker→backend transcript ingestion path (backend controller/service + persistence)
- `agents/contracts/worker-protocol.md`, `agents/contracts/data-models.md`
- backend transcript entity/repository + Liquibase changelog

## Touched Contracts
- `worker-protocol.md` (transcript ingestion payload — additive words field)
- `data-models.md` (transcript word persistence)

## Schema Impact
- additive: new `transcript_word` storage (table or JSON column) for `(word, start_sec, end_sec)`; Liquibase migration; backward-compatible with word-less transcripts.

## Acceptance Criteria
- Worker emits per-word `(word, start_sec, end_sec)` from the existing whisper output in the transcript payload (additive, optional).
- Backend persists words via a Liquibase migration and serves them where the transcript is exposed.
- Transcripts without words still ingest and serve correctly (backward compatible).
- Contracts (`worker-protocol.md`, `data-models.md`) updated to describe the additive word data.
- Tests: worker payload includes words; backend persists + returns them; word-less path still passes.

## Constraints
- Additive and backward-compatible; do not break existing transcript ingestion.
- Follow the existing persistence and Liquibase conventions; no new framework.
- Reuse the word data whisper already computes — do not re-transcribe.
- No unrelated refactor.

## Expected Deliverables
- code
- tests
- contract docs update
