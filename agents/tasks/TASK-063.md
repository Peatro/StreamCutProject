# TASK-063 Add Source And Artifact Retention Cleanup

## Agent
backend-agent

## Summary
Define and implement retention behavior so source files and exported artifacts do not accumulate forever without policy.

## Context
The MVP stores source material and exported artifacts, but no cleanup lifecycle is yet defined. A finished service needs explicit retention and cleanup rules.

## Scope
- define retention policy for uploaded source material and exported artifacts
- implement cleanup behavior or scheduled cleanup support
- ensure cleanup never removes active or still-required data incorrectly
- document operator expectations, defaults, and override points
- add focused tests for the cleanup rules

## Out of Scope
- cold archival tiers
- legal-hold workflows
- cross-region backup systems

## Inputs
- runtime.md
- TASK-054.md
- TASK-057.md
- src/main/java/com/peatroxd/streamcutproject

## Expected Deliverables
- code
- tests
- docs

## Constraints
- retention rules must be explicit and safe
- do not delete data still required for an in-flight or current successful job
- keep storage cleanup consistent with documented operator expectations

## Acceptance Criteria
- the service has a documented retention policy for source and artifact data
- cleanup behavior is configurable and predictable
- cleanup does not break current download semantics for still-retained successful exports
- operators can understand what will be deleted and when

## Notes
If source and artifact retention need different defaults, document that split rather than forcing a false single policy.
