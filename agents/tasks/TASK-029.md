# TASK-029 Implement Worker Claim API

## Agent
backend-agent

## Summary
Implement backend endpoints that allow a worker to claim the next queued task for processing.

## Context
The worker needs a real way to obtain work. The simplest MVP transport is backend-hosted claim/poll endpoints instead of broker infrastructure.

## Scope
- add worker-facing claim endpoint(s)
- atomically select and claim a queued task
- transition claimed work into the correct aggregate and runtime state
- emit claim-related job events

## Out of Scope
- result ingestion
- worker polling loop
- task retries
- export result handling

## Inputs
- worker-protocol.md
- state-machine.md
- TASK-026.md
- TASK-028.md

## Expected Deliverables
- controller
- service
- tests
- contract docs if needed

## Constraints
- claim semantics must avoid double-processing by multiple workers
- do not expose worker internals to public UI endpoints
- keep claim response aligned with worker protocol

## Acceptance Criteria
- a queued task can be claimed through a dedicated worker API
- claiming is atomic enough for single-node MVP execution
- claimed work leaves the queued state and enters the next explicit state
- tests verify claim behavior and state transitions

## Notes
Design for simple single-worker local runtime first, but avoid obvious multi-worker race bugs.

The endpoint name may still use legacy `claims/next` wording, but the contract should be treated as task-centric.
