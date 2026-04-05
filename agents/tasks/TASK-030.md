# TASK-030 Implement Worker Result Ingestion API

## Agent
backend-agent

## Summary
Implement backend ingestion of successful worker processing results.

## Context
The worker can already produce structured transcript, silence, analysis, and candidate outputs, but the backend has no real endpoint to accept and persist them end-to-end.

## Scope
- add worker-facing result submission endpoint
- validate successful worker payloads
- persist transcript segments, silence segments, analysis windows, and clip candidates
- transition jobs into `READY_FOR_REVIEW` after successful candidate generation
- write completion-related job events

## Out of Scope
- worker claim logic
- failure reporting
- export completion
- UI changes

## Inputs
- worker-protocol.md
- state-machine.md
- data-models.md
- TASK-026.md

## Expected Deliverables
- controller
- service
- persistence wiring
- tests

## Constraints
- ingest must be idempotent enough for MVP retries
- keep field names aligned with the documented worker protocol
- no business logic in controllers

## Acceptance Criteria
- backend accepts a valid worker success payload
- transcript, silence, analysis, and candidate data are persisted correctly
- job status becomes `READY_FOR_REVIEW` on successful ingest
- tests verify persistence and final state transition

## Notes
Prefer replacing prior generated data for the same job in a controlled way rather than accumulating duplicates.
