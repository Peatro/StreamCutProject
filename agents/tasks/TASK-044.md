# TASK-044 Negative Path QA: Invalid URL And Download Failure

## Agent
qa-agent

## Summary
Validate how the system behaves when ingest inputs are bad or source downloads fail upstream.

## Context
The happy path already works locally. Release hardening now requires explicit observation of invalid URL behavior, unreachable sources, and upstream download failures.

## Scope
- invalid URL format
- unreachable URL
- `404` source
- unsupported source behavior
- download timeout or failure handling
- backend job state and event verification

## Out of Scope
- fixing defects
- adding retry systems
- architecture changes

## Inputs
- backlog.md
- agents/contracts/api-contracts.md
- agents/contracts/state-machine.md
- TASK-042.md

## Expected Deliverables
- docs
- QA notes
- follow-up bug list if needed

## Constraints
- document observed behavior precisely
- do not reinterpret failures as feature requests
- focus on state coherence and user-facing clarity

## Acceptance Criteria
- failure behavior is explicitly observed and documented
- job states and events remain coherent
- user-facing failure behavior is described clearly enough for follow-up work

## Notes
Prefer reproducible cases over broad theoretical coverage.

## Observed On 2026-04-06
- `POST /api/jobs/url` currently accepts invalid-looking input such as `notaurl` and still creates a job.
- Negative URL ingest cases for malformed URL, connection refusal, and `404` upstream source were reproduced against the live Docker stack.
- In all observed cases the worker crashed during download handling instead of reporting a structured failure back to the backend.
- Affected jobs remained stuck in `DOWNLOADING` with no `JOB_FAILED` event and no persisted `errorMessage`.
- This is a release-blocking defect for URL ingest failure handling and should feed a backend fix task for worker exception reporting and stuck-job recovery.
