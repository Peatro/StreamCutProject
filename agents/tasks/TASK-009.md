# TASK-009 Implement Worker Dispatch Contract

## Agent
backend-agent

## Summary
Implement backend-side preparation and dispatch of worker input payloads for processing jobs.

## Context
The backend must hand off jobs to the Python worker without coupling domain code to Python internals.

## Scope
- build worker input payload according to contract
- introduce a dispatch service boundary
- mark job as QUEUED when dispatch is requested
- record dispatch-related job events

## Out of Scope
- actual message broker infrastructure
- worker result ingestion
- media processing
- retries

## Inputs
- worker-protocol.md
- state-machine.md
- data-models.md

## Expected Deliverables
- dispatch service
- contract DTOs
- status update logic
- tests

## Constraints
- keep JSON contract stable
- do not embed worker internals into controllers
- dispatch integration must be replaceable later

## Acceptance Criteria
- backend can build valid worker input payloads
- dispatch request moves job to QUEUED
- dispatch writes a job event
- tests verify payload shape and state update
