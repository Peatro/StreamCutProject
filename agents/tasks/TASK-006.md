# TASK-006 Create Job Upload API

## Agent
backend-agent

## Summary
Implement API support for creating a job from an uploaded video file.

## Context
MVP ingest supports both URL submission and direct file upload.

## Scope
- implement POST /api/jobs/upload
- accept multipart file input
- create a job with source type FILE
- persist original filename metadata

## Out of Scope
- saving the uploaded file to final storage
- worker dispatch
- duration extraction
- UI implementation

## Inputs
- api-contracts.md
- data-models.md
- state-machine.md
- TASK-010.md

## Expected Deliverables
- controller
- service
- request handling
- tests

## Constraints
- validate uploaded file presence
- status on creation must be NEW
- no media processing in controller

## Acceptance Criteria
- file upload request creates a persisted job
- created job stores original filename
- invalid upload request is handled cleanly
- tests cover happy path and validation failure
