# TASK-020 Create Candidate Persistence And Moderation API

## Agent
backend-agent

## Summary
Implement candidate persistence and moderation endpoints for approve/reject actions.

## Context
The backend must store generated clip candidates and support manual moderation before export.

## Scope
- create clip_candidate entity
- add migration
- add repository
- implement GET /api/jobs/{id}/candidates
- implement POST /api/candidates/{id}/approve
- implement POST /api/candidates/{id}/reject

## Out of Scope
- export initiation
- candidate preview media
- frontend moderation UI
- scoring logic

## Inputs
- data-models.md
- api-contracts.md
- worker-protocol.md

## Expected Deliverables
- entity
- migration
- repository
- moderation API
- tests

## Constraints
- moderation state must be explicit
- no entity exposure in API
- keep controller logic thin

## Acceptance Criteria
- clip_candidate table exists through migration
- candidates can be listed by job id
- approve and reject endpoints update moderation status correctly
- tests cover listing and moderation transitions
