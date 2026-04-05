# TASK-003 Docker Compose Setup

## Agent
infra-agent

## Summary
Provide a local docker-compose setup for backend, worker, and postgres.

## Context
The project must be runnable locally as a self-hosted web application.

## Scope
- add docker-compose file
- define backend service
- define worker service
- define postgres service
- configure required volumes/env variables at minimal level

## Out of Scope
- production deployment
- nginx
- kubernetes
- advanced observability

## Inputs
- architecture
- backend bootstrap
- worker bootstrap

## Expected Deliverables
- docker-compose.yml
- minimal env documentation if needed

## Constraints
- keep setup simple
- no overengineering
- local-first

## Acceptance Criteria
- compose starts all services
- backend is reachable
- postgres is reachable
- worker container starts successfully
