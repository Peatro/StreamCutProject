# TASK-011 Create Job API

## Agent
backend-agent

## Summary
Implement API endpoints to create a job from URL and list jobs.

## Context
Users need a way to submit work and inspect the current queue.

## Scope
- POST /api/jobs/url
- GET /api/jobs
- request/response DTOs
- service layer for job creation and listing

## Out of Scope
- file upload
- worker dispatch
- detailed job page
- transcript retrieval

## Inputs
- api-contracts.md
- data-models.md
- state-machine.md

## Expected Deliverables
- controller
- DTOs
- service
- tests

## Constraints
- thin controller
- no worker integration yet
- status on creation must be NEW

## Acceptance Criteria
- URL job can be created
- response contains created job id and status
- job listing endpoint returns persisted jobs
- invalid request is handled cleanly
