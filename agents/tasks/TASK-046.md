# TASK-046 Negative Path QA: Worker Restart During Queue Processing

## Agent
qa-agent

## Summary
Validate recovery behavior when the worker stops or restarts during queue processing.

## Context
The Docker MVP works locally, but release hardening still requires observed behavior for worker interruption during queueing, analysis, and export.

## Scope
- worker restart during queued job
- worker restart during analysis
- worker restart during export
- verify claim, retry, and recovery semantics
- verify whether ghosted or permanently stuck jobs appear

## Out of Scope
- implementing recovery improvements
- redesigning worker coordination
- distributed scheduling changes

## Inputs
- agents/contracts/state-machine.md
- agents/contracts/worker-protocol.md
- TASK-029.md
- TASK-031.md
- TASK-035.md

## Expected Deliverables
- docs
- QA notes
- follow-up bug list if needed

## Constraints
- report observed behavior exactly as it happens
- do not assume desired semantics that are not implemented
- focus on stuck-job and silent-loss risks

## Acceptance Criteria
- restart behavior is documented from observed execution
- stuck-job risks are identified clearly
- silent state loss does not go undocumented

## Notes
This task is about resilience clarity, not about reaching perfect recovery semantics in one pass.
