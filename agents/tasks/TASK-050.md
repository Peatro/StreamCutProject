# TASK-050 Validate Docker Runtime And Image Hygiene

## Agent
infra-agent

## Summary
Document and lightly harden the Docker runtime choices so the MVP is release-credible rather than just locally functional.

## Context
The local Docker stack works, but release prep still needs clearer runtime decisions around image strategy, Postgres versioning, volumes, and artifact-storage behavior.

## Scope
- review whether `postgres:15` is intentional or temporary
- verify backend image approach is acceptable for MVP
- check compose environment clarity
- verify named volumes and storage expectations are documented
- verify MinIO artifact flow is documented
- perform small compose cleanup only if clearly justified

## Out of Scope
- full production deployment redesign
- Kubernetes or cloud environment work
- replacing Docker Compose

## Inputs
- docker-compose.yml
- Dockerfile.backend
- worker/Dockerfile
- runtime.md
- backlog.md

## Expected Deliverables
- docs
- optional small config cleanup

## Constraints
- no speculative infrastructure redesign
- keep changes proportional to MVP needs
- make tradeoffs explicit if they remain temporary

## Acceptance Criteria
- Docker runtime choices are documented
- known compromises are explicit
- a new contributor can understand the local stack without archaeology

## Notes
This task can produce only docs if runtime choices are acceptable as-is.

## Observed On 2026-04-06
- The current Docker runtime is release-credible for the MVP and does not require a structural refactor before the current hardening batch continues.
- `Dockerfile.backend` and `worker/Dockerfile` both inherit from `postgres:15` and layer their runtime dependencies on top. That is a deliberate convenience compromise, not a long-term recommendation.
- MinIO is the export artifact store in compose, and backend artifact URLs are expected to flow through the MinIO service internally while using `localhost:9000` as the public endpoint in local developer flows.
- `streamcut-postgres`, `streamcut-data`, and `streamcut-minio` are named volumes that contributors should treat as part of the runtime contract.
- `docker compose down -v` remains the explicit reset path for a clean local slate.
- No application logic changes were needed for this task.
