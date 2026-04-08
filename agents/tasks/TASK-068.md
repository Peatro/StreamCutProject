# TASK-068 Expand Task Model With Retry, Backoff, And Dead-Letter Semantics

## Agent
backend-agent

## Summary
Upgrade the persisted task model so worker execution has explicit retry, backoff, and terminal dead-letter behavior instead of today's thin queued/claimed/running lifecycle.

## Context
The project already persists `worker_task` and `worker_execution`, but the current task model is still too narrow for reliable scale-out execution. `TASK-060` defines stuck-job recovery for `v1.0.0`; this task should turn that recovery contract into a fuller task lifecycle with bounded retries and inspectable terminal task state.

## Scope
- extend `worker_task` persistence with the fields needed for retry-aware execution
- add explicit retry attempt tracking and delayed requeue semantics
- add a terminal dead-letter or equivalent exhausted-retry task state
- define task retry policy by task type where practical
- update task selection logic to honor delayed availability
- add focused tests around retry, delayed requeue, and exhausted retry behavior
- document the resulting task lifecycle and recovery rules

## Out of Scope
- queue broker migration
- weighted fair scheduling
- Kubernetes or autoscaling work

## Inputs
- TASK-060.md
- agents/contracts/state-machine.md
- agents/contracts/worker-protocol.md
- Documentation/backlog.md
- src/main/java/com/peatroxd/streamcutproject
- src/main/resources/db/changelog

## Expected Deliverables
- code
- tests
- migration
- docs

## Constraints
- keep the backend as the source of truth for task state
- do not create infinite silent retries
- preserve current architecture boundaries between backend and worker
- make retry behavior explicit and explainable by operators

## Acceptance Criteria
- persisted task state records retry attempt information
- tasks can be requeued with explicit delayed availability rather than immediate blind retry
- exhausted retries end in a clear terminal task state that operators can inspect
- task recovery behavior is test-covered for at least `DOWNLOAD`, `ANALYZE`, and `EXPORT`
- docs describe which failures retry and which failures become terminal

## Notes
If field naming or schema shape changes are needed, prefer names that remain valid if the queue delivery mechanism later moves out of direct database polling.
