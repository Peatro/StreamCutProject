# TASK-010 Create Job Entity

## Agent
backend-agent

## Summary
Implement the vod_job entity and its initial persistence setup.

## Context
Jobs are the core orchestration unit in the system.

## Scope
- create vod_job entity
- create status enum
- add migration
- add repository

## Out of Scope
- create job API
- state transition rules
- worker dispatch

## Inputs
- data-models.md
- state-machine.md

## Expected Deliverables
- entity
- enum
- repository
- migration

## Constraints
- use migration-based DB changes
- no API layer work
- no service workflow logic

## Acceptance Criteria
- table exists through migration
- entity maps correctly
- status enum exists
- repository compiles and is usable
