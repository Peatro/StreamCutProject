# TASK-016 Create Silence Segment Persistence

## Agent
backend-agent

## Summary
Implement persistence for silence segments returned by the worker.

## Context
Silence intervals are required to calculate analysis metrics and score clip candidates.

## Scope
- create silence_segment entity
- add migration
- add repository
- define persistence mapping from worker result data

## Out of Scope
- silence API
- ffmpeg silencedetect integration
- analysis window calculation
- candidate scoring

## Inputs
- data-models.md
- worker-protocol.md

## Expected Deliverables
- entity
- migration
- repository
- mapping code

## Constraints
- use migration-based DB changes
- keep schema aligned with contracts
- no worker code changes in this task

## Acceptance Criteria
- silence_segment table exists through migration
- segments map correctly to a job
- persistence code stores duration and interval fields correctly
- tests cover mapping and persistence behavior
