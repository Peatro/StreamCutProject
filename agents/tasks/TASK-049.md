# TASK-049 Add Focused Integration Tests Around Critical Persistence And API Flows

## Agent
backend-agent

## Summary
Strengthen release confidence with integration coverage for the most important API and persistence flows.

## Context
The critical MVP loop works locally, but release-sensitive paths should be better protected from easy regression, especially around ingest, worker transitions, approval, export, and oversize upload rejection.

## Scope
- prioritize tests for upload job creation
- prioritize tests for URL job creation
- prioritize tests for worker claim
- prioritize tests for result ingestion
- prioritize tests for candidate approval
- prioritize tests for export creation and status
- prioritize tests for oversize upload rejection
- prioritize key state transitions

## Out of Scope
- exhaustive edge-case matrix
- UI browser testing
- worker-model correctness testing

## Inputs
- TASK-040.md
- agents/contracts/api-contracts.md
- agents/contracts/state-machine.md
- src/test/java/com/peatroxd/streamcutproject

## Expected Deliverables
- code
- tests

## Constraints
- focus on release-sensitive flows only
- keep fixtures lightweight
- avoid brittle end-to-end setup when targeted integration tests suffice

## Acceptance Criteria
- critical API and persistence paths have stronger automated coverage
- release-sensitive scenarios are protected against obvious regressions
- oversize upload rejection is covered explicitly

## Notes
Prefer a smaller number of high-signal tests over a wide but shallow matrix.

## Observed On 2026-04-06
- Added a focused Spring/H2 integration test that exercises URL job creation plus worker claim through the HTTP APIs.
- Added a second integration test that exercises candidate approval, export start, export completion, and export-status reads through the real controller/service stack.
- The test slice covers the critical persistence transitions and event history that matter for release review.
- The new coverage is intentionally small and high-signal rather than exhaustive.
