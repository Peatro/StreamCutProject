# ADR-002 Task-Centric Execution Model

## Status

Accepted

## Date

2026-04-08

## Context

The original MVP execution flow was strongly centered on `VodJob` status and worker polling behavior.

The codebase has now evolved to include:

- `worker_task`
- `worker_execution`
- heartbeat tracking
- stale recovery
- role-aware worker claiming

This means the project is already moving away from pure job-status orchestration.

The project needs an explicit architecture decision about what is primary going forward:

- `VodJob` as the execution truth
- or `worker_task` as the execution truth with `VodJob` as the operator-facing aggregate

## Decision

The project adopts a task-centric execution model.

This means:

- `worker_task` is the primary execution-facing unit
- `worker_execution` records concrete execution attempts and ownership
- `VodJob` remains the operator-facing aggregate and high-level lifecycle projection
- future retry, backoff, dead-letter, routing, and broker-delivery work will build on task state rather than hiding execution semantics in `VodJobService`

## Alternatives Considered

### Keep job-centric orchestration as the primary model

- simpler in the short term
- rejected because retries, worker pools, and later queue evolution become harder to express cleanly

### Adopt a full external workflow engine now

- could centralize orchestration
- rejected because it adds infrastructure and abstraction before the current execution semantics are fully stabilized

## Consequences

### Positive

- retry and recovery semantics have a natural home
- worker-pool separation becomes cleaner
- queue-delivery abstraction becomes easier later
- `VodJob` stays readable for operators instead of becoming a hidden orchestration monster

### Negative

- more explicit execution state must be maintained
- orchestration refactoring work is required
- some current service code will need to be decomposed

### Follow-Up

- `TASK-068`
- `TASK-069`
- `TASK-070`
- future ADRs for export-worker separation and durable artifact contract

## Related Documents

- `Documentation/backlog.md`
- `Documentation/architecture-roadmap.md`
- `Documentation/worker-scaling-roadmap.md`
- `agents/tasks/TASK-068.md`
- `agents/tasks/TASK-069.md`

## Notes

This ADR does not require immediately splitting every pipeline stage into its own task type.

It only defines the architectural direction: execution semantics should live in the task layer, not be hidden inside a job aggregate.
