# TASK-048 Add Structured Runtime Logging Around Job Lifecycle

## Agent
backend-agent

## Summary
Add targeted structured logging around the job lifecycle so one job can be traced without detective work.

## Context
The MVP currently works, but debugging still depends too much on scattered log output. Release hardening needs lightweight, consistent job-stage logging without overengineering.

## Scope
- add structured logs around job creation
- add structured logs around queue transition
- add structured logs around worker claim
- add structured logs around result ingestion
- add structured logs around export dispatch
- add structured logs around export completion and failure
- include `jobId` and stage context consistently
- document how to trace one job through logs

## Out of Scope
- full tracing stack
- metrics platform
- external observability tooling

## Inputs
- runtime.md
- TASK-044.md
- TASK-045.md
- TASK-046.md
- src/main/java/com/peatroxd/streamcutproject
- worker/src/streamcut_worker

## Expected Deliverables
- code
- docs

## Constraints
- keep logging improvements targeted and readable
- do not flood logs with low-value noise
- maintain stdout-first local runtime behavior

## Acceptance Criteria
- one job can be followed through logs without detective work
- logs help explain failures during QA and release prep
- no full observability rabbit hole is introduced

## Notes
Worker and backend logs only need enough structure for local ops and release validation.

## Observed On 2026-04-06
- Backend logs now emit structured `jobId`-keyed events for job creation, queueing, claim, result ingestion, export start, export completion, and failure.
- Worker logs now preserve backend transport failures and crash paths with stage context so release QA can trace what happened without reading stack traces only.
- The logging pass is intentionally lightweight and stdout-first; no external observability stack was introduced.
