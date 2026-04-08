# TASK-009 Implement Worker Dispatch Contract

## Agent
backend-agent

## Summary
Implement backend-side preparation of worker dispatch payloads for operational processing tasks.

## Context
The backend must hand off operational work to the Python worker without coupling domain code to Python internals.

## Scope
- build worker input payload according to contract
- introduce a dispatch service boundary
- mark aggregate state for queued worker processing when dispatch is requested
- record dispatch-related job events

## Out of Scope
- actual message broker infrastructure
- worker result ingestion
- media processing
- retries

## Inputs
- worker-protocol.md
- state-machine.md
- data-models.md

## Expected Deliverables
- dispatch service
- contract DTOs
- status update logic
- tests

## Constraints
- keep JSON contract stable
- do not embed worker internals into controllers
- dispatch integration must be replaceable later

## Acceptance Criteria
- backend can build valid worker input payloads
- dispatch request moves the aggregate into the next queued processing state
- dispatch writes a job event
- tests verify payload shape and state update

## Historical Note
This early task predates the current `worker_task` and `worker_execution` model.
Interpret its original `job` wording as aggregate-facing queue preparation rather than as the long-term operational execution identity.
