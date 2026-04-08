# TASK-069 Extract Task Transition Service And Task-Centric Claim Flow

## Agent
backend-agent

## Summary
Separate execution orchestration from `VodJobService` by introducing an explicit transition layer and making worker claims task-centric rather than job-centric.

## Context
The current codebase already has `worker_task`, but orchestration still lives largely inside `VodJobService`. That shape is acceptable for MVP evolution, but it will become a maintenance and scaling problem if task transitions, retries, and worker routing continue to be spread across one large service.

## Scope
- extract next-task creation and task transition rules into a dedicated service
- make worker claim selection operate on task availability and task type instead of implicit job-state assumptions
- keep `VodJob` as the operator-facing aggregate while moving execution rules into the task layer
- update integration and unit coverage around task transitions and claims
- document the resulting orchestration model

## Out of Scope
- introducing a distributed workflow engine
- queue broker rollout
- large UI changes unrelated to task-state visibility

## Inputs
- TASK-068.md
- TASK-060.md
- agents/contracts/state-machine.md
- Documentation/worker-scaling-roadmap.md
- src/main/java/com/peatroxd/streamcutproject

## Expected Deliverables
- code
- tests
- docs

## Constraints
- keep product-facing `VodJob` lifecycle understandable
- avoid a broad unrelated refactor of the whole backend package structure
- preserve existing transport contracts where they are still fit for purpose

## Acceptance Criteria
- task transition rules are located in a dedicated orchestration component rather than spread through `VodJobService`
- worker claim logic operates against explicit task state and availability
- `VodJob` remains the operator-facing aggregate, while task state becomes the execution-facing source of truth
- the new orchestration flow is test-covered for normal success paths and at least one failure/recovery path
- docs explain the division of responsibility between job state and task state

## Notes
Do not over-design this into a generic workflow platform. A simple, explicit transition service is the intended target.
