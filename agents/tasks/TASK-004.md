# TASK-004 Configure Persistence Foundation

## Agent
backend-agent

## Summary
Configure PostgreSQL and Liquibase as the backend persistence foundation.

## Context
The MVP requires migration-based schema management and a stable relational store before domain entities can be added safely.

## Scope
- configure PostgreSQL connection settings
- add Liquibase integration
- create the initial changelog structure
- ensure the application starts with migrations enabled

## Out of Scope
- domain entity migrations
- job APIs
- worker integration
- frontend work

## Inputs
- architecture.md
- coding-standards.md
- TASK-001.md

## Expected Deliverables
- backend config
- Liquibase setup
- initial changelog files
- startup verification

## Constraints
- use PostgreSQL
- use migration-based DB changes
- no schema design beyond foundation setup

## Acceptance Criteria
- backend starts with PostgreSQL configuration
- Liquibase runs on startup
- changelog structure is ready for future migrations
- no domain tables are introduced beyond foundation requirements
