# TASK-058 Replace MVP Container Strategy And Add Production Edge Runtime

## Agent
infra-agent

## Summary
Replace the convenience-first MVP container setup with a deployable runtime package suitable for a small self-hosted service.

## Context
The current stack works, but backend and worker images inherit from `postgres:15`, which is documented as an MVP-only compromise. `v1.0.0` needs cleaner runtime packaging and an explicit edge entrypoint.

## Scope
- replace convenience image inheritance with purpose-fit backend and worker images
- define the production-serving path for the operator UI and API behind an edge runtime or reverse proxy
- document container responsibilities, ports, and storage assumptions
- keep the local compose path available if it remains useful
- perform only the runtime cleanup needed to support a credible deployment package

## Out of Scope
- Kubernetes manifests
- service mesh
- cloud-specific provisioning

## Inputs
- TASK-050.md
- TASK-057.md
- docker-compose.yml
- Dockerfile.backend
- worker/Dockerfile

## Expected Deliverables
- Docker and runtime config changes
- docs

## Constraints
- no architecture split into additional services
- keep the deployment story small-team friendly
- preserve current backend, worker, database, and object storage boundaries
- keep the edge runtime minimal: request routing, static asset serving, and deployment clarity are enough

## Acceptance Criteria
- backend and worker images no longer depend on `postgres:15` as a runtime base
- the public entry path for UI and API is documented and reproducible
- runtime docs explain how the production package differs from the local MVP stack
- the resulting container strategy is credible for a small self-hosted deployment

## Notes
An edge runtime can stay minimal. The goal is operational clarity and cleaner packaging, not infrastructure theater.

## Implementation Notes
- Replace `postgres:15` inheritance with purpose-fit backend and worker images.
- Keep `docker-compose.yml` as the local path; use a separate production compose package rather than overloading the local stack.
- A minimal reverse proxy is enough for the production edge runtime if it gives one clear public entrypoint for UI and API traffic.
- It is acceptable for the production package to keep PostgreSQL and S3-compatible object storage as external dependencies as long as that contract is documented explicitly.
