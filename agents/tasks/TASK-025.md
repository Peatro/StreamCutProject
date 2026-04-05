# TASK-025 Harden MVP Runtime

## Agent
infra-agent

## Summary
Harden the local MVP runtime with operational basics for errors, cleanup, and developer setup.

## Context
The project needs a usable local runtime beyond feature completeness to support repeated development and smoke testing.

## Scope
- document local startup and required environment variables
- define cleanup policy for generated artifacts
- add basic runtime logging expectations and service notes
- ensure dockerized local run covers backend, worker, and postgres coherently

## Out of Scope
- production deployment
- Kubernetes
- advanced observability stack
- feature development

## Inputs
- workflow.md
- architecture.md
- TASK-003.md

## Expected Deliverables
- docs
- runtime config updates
- cleanup notes or scripts
- compose adjustments if needed

## Constraints
- keep local-first setup simple
- avoid infrastructure overkill
- focus on MVP operability

## Acceptance Criteria
- local run instructions are documented end-to-end
- cleanup expectations for generated files are defined
- compose/runtime configuration is consistent with the implemented services
- a developer can understand how to start and inspect the MVP locally
