# TASK-037 Add End-To-End Integration Coverage

## Agent
backend-agent

## Summary
Add integration coverage for the full backend-side lifecycle around job queueing, worker callbacks, and export orchestration.

## Context
The project has unit-level and smoke-level confidence, but full service readiness requires automated coverage of the critical orchestration paths.

## Scope
- add integration tests for create -> queue flow
- add integration tests for worker success ingest
- add integration tests for worker failure ingest
- add integration tests for export request and export completion paths

## Out of Scope
- browser automation
- real ffmpeg execution in CI
- production load testing

## Inputs
- TASK-028.md
- TASK-029.md
- TASK-030.md
- TASK-031.md
- TASK-035.md

## Expected Deliverables
- tests
- test fixtures

## Constraints
- keep tests deterministic
- focus on orchestration and persistence outcomes
- do not depend on external services

## Acceptance Criteria
- automated tests cover the critical backend lifecycle transitions
- tests verify persisted state and job events
- failures in worker callback handling are caught by the suite
- export-state regressions are covered

## Notes
Favor integration tests that assert persisted data over excessive mocking.
