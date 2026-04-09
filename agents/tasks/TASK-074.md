# TASK-074 Prepare Queue Delivery Abstraction For Broker Migration

## Agent
infra-agent

## Summary
Define and prepare the queue-delivery abstraction so the project can later move beyond direct database polling without rewriting task state semantics.

## Context
The current architecture should not jump straight to RabbitMQ or Kubernetes, but the code should stop assuming that task delivery and durable task state are the same thing. This task prepares that seam after the task model and transition logic have been cleaned up.

## Scope
- document the boundary between durable task state and delivery/reservation mechanics
- identify the backend and worker interfaces that must remain stable if delivery moves away from direct database polling
- prepare lightweight implementation seams or abstractions that allow broker adoption later
- keep PostgreSQL-backed task state as the source of truth
- document the migration criteria for when a separate broker is actually justified

## Out of Scope
- deploying RabbitMQ, Redis, SQS, or another broker
- Kubernetes rollout
- throughput benchmarking campaign

## Inputs
- TASK-068.md
- TASK-069.md
- Documentation/backlog.md
- Documentation/worker-scaling-roadmap.md
- agents/contracts/worker-protocol.md
- src/main/java/com/peatroxd/streamcutproject
- worker

## Expected Deliverables
- docs
- code only if a small abstraction seam is needed

## Constraints
- do not add infrastructure just to look more scalable
- keep durable task state in the backend-owned persistence model
- avoid speculative abstraction layers that are broader than the actual migration seam

## Acceptance Criteria
- the project documents a clear separation between task state and task delivery
- backend and worker code have an intentional seam that would allow later broker-backed delivery
- the migration trigger conditions are documented so broker adoption stays evidence-based
- no part of the system starts treating a future broker as the sole source of business truth

## Notes
This task is about architectural preparation, not infrastructure rollout. If the best outcome is mostly documentation plus a thin interface seam, that is acceptable.
