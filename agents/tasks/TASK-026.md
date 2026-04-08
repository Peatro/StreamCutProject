# TASK-026 Freeze Worker Transport Contract

## Agent
backend-agent

## Summary
Define and implement the MVP transport contract between backend and worker using a stable HTTP polling/callback model.

## Context
The current project has worker payload DTOs and a no-op dispatch port, but it does not yet have a real transport model for claiming work and returning results. This must be fixed before the service can process VOD processing end-to-end.

## Scope
- choose the concrete MVP worker transport approach
- document the transport endpoints and payload directions
- update backend-side contract DTOs if needed
- align `worker-protocol.md` and `api-contracts.md` with the chosen flow

## Out of Scope
- task claim implementation
- result ingestion implementation
- worker polling loop
- media processing

## Inputs
- worker-protocol.md
- api-contracts.md
- state-machine.md
- TASK-009.md

## Expected Deliverables
- docs
- contract updates
- DTO adjustments if needed

## Constraints
- keep the contract JSON-stable
- do not introduce a broker in MVP
- no architecture change beyond making the transport explicit

## Acceptance Criteria
- backend and worker communication model is explicit and documented
- request/response shapes for claim, result, and failure paths are defined
- the chosen transport is simple enough for local Docker runtime
- downstream tasks can implement against the documented contract without ambiguity

## Notes
Prefer HTTP polling plus result callback for MVP. Avoid queue infrastructure unless the current stack proves insufficient.

Legacy wording in downstream tasks may still say `job claim`, but this contract should now be interpreted through the current task-centric execution model.
