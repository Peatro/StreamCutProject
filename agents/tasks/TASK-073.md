# TASK-073 Add Product Quotas And Runtime Limits

## Agent
backend-agent

## Summary
Introduce the first explicit quota and runtime limit layer so a single user or pathological input cannot dominate the pipeline.

## Context
The project currently has technical upload size controls, but it does not yet have product-level quotas or runtime guards aligned with the target architecture. Before growth, the service needs basic concurrency, duration, and export limits.

## Scope
- define and implement baseline limits for job creation and export activity
- add max concurrent jobs per user or operator identity where the current auth model supports it
- add max duration or equivalent input guard for VOD processing
- expose clear failure responses when limits are exceeded
- document the initial quota and limit policy

## Out of Scope
- billing integration
- multi-plan subscription system
- weighted fairness scheduler

## Inputs
- TASK-055.md
- TASK-068.md
- Documentation/backlog.md
- Documentation/runtime.md
- src/main/java/com/peatroxd/streamcutproject
- src/main/resources/static

## Expected Deliverables
- code
- tests
- docs

## Constraints
- limits must be explicit and operator-comprehensible
- do not invent a complex pricing or plan system for this task
- enforce limits before expensive processing begins where possible

## Acceptance Criteria
- the service enforces documented baseline limits for at least concurrent work and input duration or size
- attempts to exceed limits fail clearly and predictably
- the limit policy is documented in operator-facing runtime docs
- tests cover normal acceptance and rejection paths for the new guardrails

## Notes
Start with a small practical set of limits. The goal is operational protection, not a full commercial entitlements system.
