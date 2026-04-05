# TASK-008 Add Job Event Persistence And API

## Agent
backend-agent

## Summary
Implement job event storage and an API endpoint to retrieve job events.

## Context
The MVP uses job events to expose pipeline progress and failures in the UI.

## Scope
- create job_event entity
- add migration
- add repository
- implement GET /api/jobs/{id}/events

## Out of Scope
- retries
- real worker event publishing
- websocket updates
- UI rendering

## Inputs
- data-models.md
- api-contracts.md
- state-machine.md

## Expected Deliverables
- entity
- migration
- repository
- API endpoint
- tests

## Constraints
- use migration-based DB changes
- keep event payload simple
- no background infrastructure additions

## Acceptance Criteria
- job_event table exists through migration
- events can be retrieved by job id
- response order is stable and predictable
- tests cover persistence and API behavior
