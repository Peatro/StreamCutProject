# TASK-031 Implement Worker Failure Reporting API

## Agent
backend-agent

## Summary
Implement backend ingestion of worker failures and explicit failure state transitions.

## Context
The planned system requires jobs to fail loudly and predictably. A worker that cannot report failures back to backend leaves jobs stuck in ambiguous states.

## Scope
- add worker-facing failure reporting endpoint
- accept structured failure payloads
- move jobs to `FAILED`
- store failure messages
- record failure events

## Out of Scope
- retry scheduling
- dead-letter queues
- success result ingestion
- export retries

## Inputs
- state-machine.md
- data-models.md
- TASK-026.md

## Expected Deliverables
- controller
- service
- tests
- contract doc updates if needed

## Constraints
- failure transitions must be explicit
- error messages should be useful but not overloaded with sensitive internals
- keep transport shape simple for worker implementation

## Acceptance Criteria
- worker can report a processing failure through a dedicated API
- reported jobs become `FAILED`
- failure message and job event are persisted
- tests verify state, message, and event behavior

## Notes
Support failures from any processing stage, not only transcription or export.
