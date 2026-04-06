# TASK-060 Harden Queue Reliability And Stuck-Job Recovery

## Agent
backend-agent

## Summary
Make job processing resilient to worker loss, partial failures, and ambiguous in-flight state so jobs do not stay ghosted indefinitely.

## Context
The MVP queue loop works, and `TASK-046` showed partial restart resilience, but `v1.0.0` needs explicit recovery semantics rather than best-effort behavior.

## Scope
- define claim lease, timeout, and reclaim semantics
- detect and recover or fail jobs that remain stuck in in-flight states too long
- document retry and recovery rules for queue, ingest, and export stages
- add focused automated coverage for the critical recovery paths
- ensure state transitions remain coherent and observable

## Out of Scope
- distributed queue migration
- horizontal autoscaling policy
- speculative throughput optimization

## Inputs
- TASK-046.md
- TASK-059.md
- agents/contracts/state-machine.md
- agents/contracts/worker-protocol.md
- src/main/java/com/peatroxd/streamcutproject
- worker

## Expected Deliverables
- code
- tests
- docs

## Constraints
- preserve current architecture boundaries
- keep recovery semantics explicit and finite
- do not hide repeated failures by silently looping forever

## Acceptance Criteria
- worker death or disconnect does not leave jobs ghosted indefinitely
- claim expiry or renewal rules are explicit and documented
- stuck jobs are either reclaimed, retried under explicit rules, or failed clearly within documented timeout windows
- recovery behavior is documented and test-covered for at least queued, active analysis, and active export states
- operators can understand why a recovery action happened

## Notes
This task defines the system recovery contract. Follow-up operator controls in `TASK-061` should build on that contract rather than inventing separate behavior.
