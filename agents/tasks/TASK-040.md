# TASK-040 Implement Backend Multipart Limits And Upload Error Handling

## Agent
backend-agent

## Summary
Fix the known `413` upload failure by configuring explicit multipart limits and stable backend error handling.

## Context
Realistic MVP uploads currently fail at default framework limits. Release hardening requires explicit limits, predictable error semantics, and test coverage for both accepted and rejected uploads.

## Scope
- configure Spring multipart limits explicitly
- ensure backend returns a predictable structured error for exceeded size
- ensure the error response is user-consumable
- verify no upload-flow regressions are introduced
- add or adjust tests for upload size boundaries

## Out of Scope
- redesigning job creation
- storage architecture changes
- chunked or resumable upload support

## Inputs
- TASK-039.md
- agents/contracts/api-contracts.md
- src/main/resources/application.yaml
- src/main/java/com/peatroxd/streamcutproject/vodjob
- src/test/java/com/peatroxd/streamcutproject/vodjob

## Expected Deliverables
- code
- tests
- config
- docs

## Constraints
- preserve current upload API shape
- no unrelated refactor
- keep error semantics stable and explicit

## Acceptance Criteria
- upload no longer fails unexpectedly at default framework limits
- oversized files fail with explicit and stable error semantics
- normal-sized realistic uploads succeed
- tests cover both successful and oversize uploads

## Notes
Coordinate with `TASK-041` only through stable error semantics, not through ad hoc UI assumptions.
