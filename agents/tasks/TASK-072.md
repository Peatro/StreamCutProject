# TASK-072 Make Object Storage The Durable Artifact Contract

## Agent
backend-agent

## Summary
Promote object storage references from an export-only detail to the durable artifact contract for the execution pipeline.

## Context
The system already persists completed exports into MinIO or S3-compatible storage, but the pipeline still relies in places on host-local paths as part of its effective contract. That is workable on one machine, but it blocks clean scale-out and weakens resumability across nodes.

## Scope
- define which artifacts must live behind durable storage references
- move the pipeline toward durable references for source and final artifacts, and for intermediate artifacts where necessary
- reduce dependence on host-local paths as cross-component contracts
- keep local disk as scratch space or cache rather than durable shared truth
- document the resulting storage contract and operator expectations

## Out of Scope
- broker migration
- CDN rollout
- cold archival or multi-region storage strategy

## Inputs
- TASK-071.md
- TASK-063.md
- Documentation/STORAGE.md
- Documentation/worker-scaling-roadmap.md
- src/main/java/com/peatroxd/streamcutproject/storage
- worker

## Expected Deliverables
- code
- tests where appropriate
- docs
- config if needed

## Constraints
- preserve recoverability and reproducibility of core jobs
- do not require every temporary artifact to become durable if it is cheaper and safe to recreate
- keep the storage contract explicit about what is durable versus scratch-only

## Acceptance Criteria
- the project documents which artifacts are durable and where they live
- core execution stages can refer to durable artifact identifiers instead of assuming host-local continuity
- local scratch files are no longer treated as the long-term system contract
- the updated storage model is reflected in code and runtime documentation

## Notes
The goal is not to store every intermediate by default. The goal is to make the contract explicit and durable where retries, recovery, or cross-node execution require it.
