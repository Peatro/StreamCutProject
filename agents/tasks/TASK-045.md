# TASK-045 Negative Path QA: Export Failure And Artifact Failure

## Agent
qa-agent

## Summary
Validate that export-stage failures and artifact retrieval problems do not leave the system in an ambiguous state.

## Context
The export path now works on the happy path. Release hardening still requires observing how export and artifact failures behave in practice.

## Scope
- simulate failed ffmpeg export
- verify export status transitions
- verify event persistence
- verify failed artifact retrieval behavior
- verify repeated export attempts if applicable

## Out of Scope
- implementing export retries
- changing storage architecture
- performance tuning

## Inputs
- agents/contracts/state-machine.md
- agents/contracts/worker-protocol.md
- TASK-035.md
- TASK-036.md

## Expected Deliverables
- docs
- QA notes
- recommended follow-up tasks if needed

## Constraints
- focus on behavior, not implementation preference
- tie findings back to state and UI clarity
- do not introduce new feature scope

## Acceptance Criteria
- export failure behavior is observed and documented
- the system does not hang in an ambiguous intermediate state without being reported
- backend and UI messaging are understandable enough for release review

## Notes
If repeated export attempts are not supported, document actual current behavior explicitly.

## Observed On 2026-04-06
- Repeated export attempts are currently allowed by the backend when a candidate is `APPROVED` and not already `IN_PROGRESS`.
- Export-stage failure was reproduced on `candidate 2` by truncating the FILE source asset before export. The worker reported `EXPORTING_CLIP` failure, the backend persisted `JOB_FAILED`, and the job ended in `FAILED` with `errorMessage = "ffmpeg failed while exporting clip"`.
- Export transitions observed on the failed retry were `EXPORT_STARTED -> JOB_CLAIMED -> JOB_FAILED`.
- During the failed retry, `/api/exports/2/file` and `/api/exports/2/stream` still returned `200` because the previous artifact remained on disk. That is a stale-artifact behavior worth follow-up, not clean failure semantics.
- After restoring the source file, a repeated export attempt on the same candidate succeeded again and moved back to `COMPLETED`.
