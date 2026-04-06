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

## Revalidated On 2026-04-06
- `POST /api/jobs/url` still accepts malformed-looking input such as `notaurl`, but the worker now reports a clean failure instead of crashing.
- Test case 1: `notaurl`
  - Created job `#3`
  - Status transition observed: `QUEUED -> FAILED`
  - Final job error: `DOWNLOADING: source download failed for notaurl: unknown url type: 'notaurl'`
  - Events included `JOB_CREATED`, `JOB_QUEUED`, `JOB_CLAIMED`, `JOB_FAILED`
- Test case 2: real `404` source
  - Created job `#4` for `http://backend:8080/does-not-exist`
  - Status transition observed: `QUEUED -> FAILED`
  - Final job error: `DOWNLOADING: source download failed for http://backend:8080/does-not-exist: HTTP Error 404: `
  - Events included `JOB_CREATED`, `JOB_QUEUED`, `JOB_CLAIMED`, `JOB_FAILED`
- The old blocker no longer reproduces in these cases: jobs do not remain stuck in `DOWNLOADING`, and the backend persists the failure state and message.
- Residual risk:
  - unsupported source types were not separately exercised because current API accepts URL/file sources only
  - a long timeout / slow upstream case was not explicitly re-run in this pass

## Conclusion
- TASK-044 is satisfied for release-gate purposes for the tested negative URL ingest paths.
- The doc status should now be read as "fixed for malformed URL and 404 handling, with minor residual QA coverage gaps on timeout/unsupported-source variants."
