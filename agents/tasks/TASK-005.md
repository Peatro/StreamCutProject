# TASK-005 Create Job Details API

## Agent
backend-agent

## Summary
Implement the endpoint to retrieve one job by id with its core metadata.

## Context
The MVP needs a job details page and backend clients need a stable way to inspect one job state.

## Scope
- implement GET /api/jobs/{id}
- add response DTO
- add service method for job lookup
- handle job-not-found cleanly

## Out of Scope
- transcript retrieval
- candidate retrieval
- event retrieval
- export status

## Inputs
- api-contracts.md
- data-models.md
- TASK-010.md
- TASK-011.md

## Expected Deliverables
- controller
- DTO
- service
- tests

## Constraints
- thin controller
- no entity exposure in API
- response shape must be stable

## Acceptance Criteria
- GET /api/jobs/{id} returns persisted job metadata
- unknown job id returns a clear error response
- DTO does not expose JPA entity directly
- endpoint tests cover success and not-found cases
