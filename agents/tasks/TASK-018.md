# TASK-018 Create Analysis Window Persistence

## Agent
backend-agent

## Summary
Implement persistence for analysis windows produced by the worker.

## Context
Analysis windows store the intermediate scoring metrics used to derive clip candidates.

## Scope
- create analysis_window entity
- add migration
- add repository
- define persistence mapping from worker result data

## Out of Scope
- worker metric calculation
- candidate persistence
- moderation API
- UI work

## Inputs
- data-models.md
- worker-protocol.md

## Expected Deliverables
- entity
- migration
- repository
- mapping code

## Constraints
- schema must align with data-models.md
- use migration-based DB changes
- no scoring formula changes in backend

## Acceptance Criteria
- analysis_window table exists through migration
- all metric fields map correctly
- windows are linked to the correct job
- tests cover mapping and persistence behavior
