# TASK-028 Auto Queue Jobs On Creation

## Agent
backend-agent

## Summary
Queue jobs automatically when they are created and record creation/queue events.

## Context
Right now job creation only inserts a `vod_job` row with status `NEW`. The planned service requires newly created jobs to progress immediately into the processing pipeline.

## Scope
- trigger queueing after successful job creation
- move jobs from `NEW` to `QUEUED`
- record explicit job events for creation and queueing
- ensure both URL jobs and upload jobs follow the same queueing path

## Out of Scope
- worker claim API
- worker result ingestion
- retries
- export flow

## Inputs
- state-machine.md
- data-models.md
- TASK-011.md
- TASK-027.md

## Expected Deliverables
- code
- tests

## Constraints
- state transitions must stay explicit
- do not skip directly to processing states from controllers
- keep queueing logic inside services/orchestration layer

## Acceptance Criteria
- newly created jobs end in `QUEUED`
- job events are written for creation and queueing
- both URL and upload paths use the same orchestration logic
- tests verify state and event persistence

## Notes
Use this task to make job creation operational, not to implement worker-side consumption.
