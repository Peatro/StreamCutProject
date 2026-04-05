# TASK-012 Create Transcript Persistence

## Agent
backend-agent

## Summary
Implement persistence for transcript segments returned by the worker.

## Context
Transcript data is a core intermediate result used for review and candidate scoring.

## Scope
- create transcript_segment entity
- add migration
- add repository
- define persistence mapping from worker contract shape

## Out of Scope
- transcript API
- worker transcription logic
- transcript search
- excerpt generation beyond stored data

## Inputs
- data-models.md
- worker-protocol.md

## Expected Deliverables
- entity
- migration
- repository
- mapping code

## Constraints
- schema must follow the defined data model
- use migration-based DB changes
- no UI work

## Acceptance Criteria
- transcript_segment table exists through migration
- segments map correctly to a job
- persistence layer can store multiple ordered segments for one job
- code compiles and tests cover mapping/persistence
