# TASK-064 Add Browser E2E Regression And CI Gate

## Agent
qa-agent

## Summary
Protect the main operator flows with automated browser coverage and a CI gate that runs on every release-critical change.

## Context
Manual browser QA is necessary, but `v1.0.0` also needs repeatable regression protection so release discipline does not depend on memory.

## Scope
- add browser E2E coverage for the critical operator happy path
- include at least one key failure-path assertion where it adds real confidence
- wire the E2E suite into CI for release-critical branches or PRs
- document how to run the suite locally and how to interpret failures
- keep the suite focused on stable high-signal flows

## Out of Scope
- exhaustive visual-regression platform
- broad cross-browser matrix
- flaky low-value UI tests

## Inputs
- TASK-042.md
- TASK-043.md
- TASK-053.md
- runtime.md
- src/main/resources/static

## Expected Deliverables
- test code
- CI config
- docs

## Constraints
- prefer a small stable suite over broad but noisy coverage
- test the operator experience against a real running stack
- do not encode brittle implementation details into the tests

## Acceptance Criteria
- the main browser flow has automated end-to-end coverage
- the E2E suite runs as a required CI gate for release-critical branches or PRs
- CI fails when a release-critical browser regression is introduced
- local execution of the suite is documented and reproducible
- the suite is stable enough to be a gate instead of a suggestion

## Notes
Start with the narrowest suite that would have caught the most likely regressions already seen in this project.
