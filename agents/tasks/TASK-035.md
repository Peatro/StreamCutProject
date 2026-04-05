# TASK-035 Implement Export Dispatch And Completion

## Agent
backend-agent

## Summary
Route approved clip export through the same worker transport and finalize export state on completion or failure.

## Context
The current export API changes backend status immediately, but it does not yet run a real export job through the worker or receive structured completion feedback.

## Scope
- dispatch export work to worker for approved candidates
- distinguish export jobs from analysis jobs in transport payloads if required
- ingest export success and failure callbacks
- move jobs and candidates to their correct final export states
- record export-related job events

## Out of Scope
- frontend redesign
- background retries
- artifact CDN or external publishing

## Inputs
- worker-protocol.md
- api-contracts.md
- state-machine.md
- TASK-024.md
- TASK-030.md
- TASK-031.md

## Expected Deliverables
- code
- tests
- contract updates if required

## Constraints
- approved-candidate gating must remain explicit
- export must reuse the established worker transport model
- do not introduce separate ad hoc export orchestration paths

## Acceptance Criteria
- export request results in real worker-dispatched work
- export success updates artifact path and terminal export state correctly
- export failure moves the job to `FAILED` or another explicit documented state
- tests verify approval gate and callback handling

## Notes
If the existing worker protocol needs extension for export callbacks, document that precisely and minimally.
