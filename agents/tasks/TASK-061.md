# TASK-061 Add Operator Recovery Controls

## Agent
backend-agent

## Summary
Let operators retry, requeue, or cancel jobs through the product instead of resorting to database surgery.

## Context
Once recovery semantics are defined, the service still needs explicit operator controls to act on failed or stuck work safely.

## Scope
- add operator-facing recovery actions for the states supported by `TASK-060`
- expose recovery actions through stable backend APIs
- surface the controls in the UI where operators diagnose jobs
- persist clear event history for manual recovery actions
- document allowed actions and their effects

## Out of Scope
- bulk-admin tooling
- multi-user audit dashboard
- arbitrary state editing

## Inputs
- TASK-060.md
- TASK-047.md
- agents/contracts/state-machine.md
- src/main/java/com/peatroxd/streamcutproject
- src/main/resources/static

## Expected Deliverables
- code
- tests
- docs

## Constraints
- recovery controls must respect the state machine
- do not expose actions that bypass invariants
- keep operator wording explicit and low-ambiguity

## Acceptance Criteria
- operators can trigger the supported recovery actions without database access
- every manual recovery action leaves a readable event trail
- the UI only shows actions that are valid for the current state
- docs explain when retry, requeue, or cancel should be used

## Notes
If the supported action set stays intentionally small for `v1.0.0`, that is acceptable as long as it covers the real recovery cases.
