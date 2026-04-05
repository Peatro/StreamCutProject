# TASK-023 Implement Export API And Status Tracking

## Agent
backend-agent

## Summary
Implement backend endpoints and state updates for clip export requests and export status retrieval.

## Context
Export is a separate workflow step after moderation and needs explicit status tracking.

## Scope
- implement POST /api/candidates/{id}/export
- implement GET /api/exports/{id}
- update job status to EXPORTING_CLIP when export starts
- record export-related job events

## Out of Scope
- actual ffmpeg clip cutting
- file download streaming
- UI implementation
- retry policy

## Inputs
- api-contracts.md
- state-machine.md
- data-models.md
- TASK-020.md

## Expected Deliverables
- controller
- service
- DTOs
- tests

## Constraints
- export only after moderation approval
- keep status transitions explicit
- no worker logic in controllers

## Acceptance Criteria
- approved candidate can trigger export request
- export status endpoint returns current export state and artifact reference placeholder
- job status moves to EXPORTING_CLIP when export starts
- tests cover valid and invalid export initiation flows
