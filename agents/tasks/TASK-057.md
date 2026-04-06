# TASK-057 Introduce Production Runtime Profiles And Secret Handling

## Agent
infra-agent

## Summary
Separate local-development runtime assumptions from a deployable production profile with explicit secret handling.

## Context
`runtime.md` currently documents a local-first MVP environment. `v1.0.0` needs a production-oriented runtime profile that does not rely on checked-in local defaults.

## Scope
- define local versus production runtime profiles
- move secrets and sensitive runtime configuration to explicit environment inputs
- document required production environment variables and defaults policy
- ensure storage, database, and auth configuration are profile-driven rather than local-only assumptions
- keep local Docker ergonomics intact

## Out of Scope
- cloud-provider lock-in
- Kubernetes
- secret manager product integration

## Inputs
- runtime.md
- docker-compose.yml
- src/main/resources/application.yaml
- TASK-055.md
- TASK-056.md

## Expected Deliverables
- config
- docs
- optional small runtime cleanup

## Constraints
- preserve an easy local developer path
- do not hardcode production secrets into repo-managed files
- make the production profile explicit rather than implied

## Acceptance Criteria
- production runtime configuration can be supplied without editing source files
- required secrets and env vars are documented in one place
- local and production assumptions are clearly separated
- configuration behavior is testable or directly verifiable

## Notes
This task defines runtime contract clarity, not full deployment packaging.
