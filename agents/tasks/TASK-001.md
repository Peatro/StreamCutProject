# TASK-001 Bootstrap Backend

## Agent
backend-agent

## Summary
Create the initial Spring Boot backend skeleton for the project.

## Context
The backend is the orchestration layer for jobs, storage metadata, moderation, and export flow.

## Scope
- create Spring Boot project
- configure base package structure
- add health endpoint
- ensure application starts successfully

## Out of Scope
- database integration
- job logic
- worker integration
- UI implementation

## Inputs
- global architecture
- coding standards

## Expected Deliverables
- backend project scaffold
- health endpoint
- basic package structure

## Constraints
- Java 21
- Spring Boot
- no extra frameworks beyond baseline app setup
- no unrelated functionality

## Acceptance Criteria
- application starts
- GET /health returns success response
- package structure includes at least:
  - config
  - job
  - storage
- build passes
