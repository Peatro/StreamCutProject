# TASK-094 Supply Clip-Local Word Timings In The Export-Claim Payload

## Agent
backend-agent

## Summary
The export-worker is ready to burn karaoke captions (TASK-092) but the backend's export-claim payload does not yet carry the per-word timings. Add `clipWords` — the persisted transcript words (TASK-091) that fall inside the clip's `[startSec, endSec]` — to the export task claim response. Read-only; no new data, just deliver what TASK-091 already persisted.

## Context
TASK-091 persists per-word `(word, start_sec, end_sec)` on transcript segments (`words_json`). TASK-092 made the export-worker fully ready to consume them: `ClaimedJob.clip_words` exists, `BackendClient` parses `clipWords`, and `WorkerJobRunner._export()` converts them to `WordTiming` for the ASS karaoke generator. The missing link is the backend: the export-claim payload (`WorkerDispatchPayload` / its export factory) does not populate `clipWords`, so captions currently degrade to a graceful no-op. This task closes that gap.

## Problem Frame
- Symptom: karaoke captions never appear because the export-worker receives no word timings.
- Suspected Layer: backend export-claim assembly (`WorkerDispatchPayload` + the export-candidate factory + transcript word query).
- Touched Contracts: worker-protocol (export claim payload gains an additive `clipWords` array).
- Done Criterion: claiming an export task returns `clipWords` containing the persisted words within the clip window; the export-worker burns captions end-to-end.

## Scope
- In the backend's export-claim assembly (e.g. `WorkerDispatchPayloadFactory.fromExportCandidate()` or the equivalent path that builds the export claim DTO), query the job's transcript segments + their persisted words (TASK-091 `words_json`) and include the words whose timing falls within the clip's `[startSec, endSec]` as a `clipWords` array of `{word, startSec, endSec}`.
- Add the `clipWords` field to the export-claim payload DTO/record (additive, optional — empty/absent is valid).
- Keep it read-only: reuse the existing transcript word persistence/mapper from TASK-091 (`TranscriptSegmentPersistenceMapper.wordsFromJson` etc.); do not re-derive or re-transcribe.
- Backward-compatible: jobs whose transcripts have no words (legacy / words disabled) return an empty `clipWords`, and the export still works (caption-less).

## Out of Scope
- No worker-side change (TASK-092 already consumes `clipWords`); only verify the field name/shape matches what the worker parses (`clipWords` → `{word, startSec, endSec}`).
- No subtitle rendering (TASK-092).
- No schema change (words are already persisted by TASK-091).
- No change to non-export claim payloads.

## Inputs
- the backend export-claim/dispatch assembly (`WorkerDispatchPayload`, its export factory)
- `TranscriptSegment` / `TranscriptSegmentPersistenceMapper` (TASK-091 word persistence + `wordsFromJson`)
- transcript repository (query segments/words by job)
- worker side for the exact expected shape: `worker/src/streamcut_worker/services/backend_client.py` (`clipWords` parsing) and `models/transport.py` (`ClaimedJob.clip_words`)
- `agents/contracts/worker-protocol.md`

## Touched Contracts
- `worker-protocol.md` (export claim payload — additive `clipWords` array)

## Schema Impact
- none (reads TASK-091 `words_json`)

## Acceptance Criteria
- Claiming an export task returns `clipWords` = persisted words within the clip `[startSec, endSec]`, shaped `{word, startSec, endSec}` to match the worker parser.
- Jobs without word data return empty `clipWords` and still export (caption-less).
- Read-only reuse of TASK-091 persistence; no re-transcription, no schema change.
- `worker-protocol.md` updated to describe the additive export-claim `clipWords`.
- Tests cover: export claim includes in-window words; out-of-window words excluded; word-less job returns empty.

## Constraints
- Additive + backward-compatible; match the worker's expected `clipWords` shape exactly.
- Reuse existing transcript word query/mapper; no duplication.
- No unrelated refactor of the claim/dispatch path.

## Expected Deliverables
- code
- tests
- contract docs update
