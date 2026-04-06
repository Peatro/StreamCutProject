# TASK-047 Improve Failure State Visibility In Backend And UI

## Agent
backend-agent

## Summary
Make ingest and export failures understandable in both API responses and the job details UI.

## Context
The current MVP exposes statuses and events, but failure visibility is still too implicit for release-safe operator use. This task should improve practical clarity without becoming an observability platform project.

## Scope
- normalize obvious failure reason visibility
- improve event text or exposed error summaries
- make the job details page surface current failure reason clearly
- ensure export and ingest failures are distinguishable

## Out of Scope
- full observability platform work
- distributed tracing
- major UI redesign

## Inputs
- TASK-044.md
- TASK-045.md
- TASK-046.md
- src/main/java/com/peatroxd/streamcutproject
- src/main/resources/static

## Expected Deliverables
- code
- tests where appropriate
- docs if payload contracts change

## Constraints
- preserve current architecture boundaries
- focus on practical human-readable failure clarity
- avoid broad refactor unrelated to failure visibility

## Acceptance Criteria
- failures are visible in a human-readable way
- debugging and support flow are easier
- users no longer have to infer what broke from vague status alone

## Notes
If UI and backend work split naturally during execution, keep backend as the primary owner and scope UI changes tightly.
