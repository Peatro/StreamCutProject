# TASK-013 Add Transcript API

## Agent
backend-agent

## Summary
Implement the transcript endpoint for retrieving transcript segments by job.

## Context
The UI needs transcript preview data and backend consumers need a stable read API.

## Scope
- implement GET /api/jobs/{id}/transcript
- return transcript segments ordered by start time
- add DTOs
- handle missing job or empty transcript cleanly

## Out of Scope
- transcript search
- candidate generation
- transcript editing
- worker integration

## Inputs
- api-contracts.md
- data-models.md
- TASK-012.md

## Expected Deliverables
- controller
- DTOs
- service
- tests

## Constraints
- no entity exposure in API
- response order must be stable
- keep controller thin

## Acceptance Criteria
- transcript endpoint returns stored segments for a job
- segments are ordered by start time
- empty transcript response is handled predictably
- tests cover success and no-data behavior
