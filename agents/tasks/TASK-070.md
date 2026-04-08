# TASK-070 Introduce Dedicated `export-worker` Pool

## Agent
worker-agent

## Summary
Split export execution away from the current mixed processing worker role so analysis and clip rendering can scale and fail independently.

## Context
The system already separates `download-worker` and `processing-worker`, but export still runs on the processing side. That keeps the current runtime simple, yet it couples two workloads with different CPU and I/O profiles and weakens the architecture direction documented in the backlog and scaling roadmap.

## Scope
- add an explicit `export-worker` role to the worker runtime
- route `EXPORT` tasks only to the new export role
- keep analysis work on the processing or analyze side without export contention
- update local and production runtime packaging for the new worker role
- add focused validation or tests around role-aware claim behavior and export execution
- document the new worker topology

## Out of Scope
- GPU worker specialization
- autoscaling policy
- broker migration

## Inputs
- TASK-069.md
- TASK-060.md
- Documentation/worker-scaling-roadmap.md
- docker-compose.yml
- docker-compose.production.yml
- worker
- src/main/java/com/peatroxd/streamcutproject

## Expected Deliverables
- code
- tests where appropriate
- config
- docs

## Constraints
- keep worker responsibilities explicit and low-ambiguity
- do not merge analysis and export ownership back together through fallback shortcuts
- preserve the existing backend <-> worker transport contract unless a change is necessary and documented

## Acceptance Criteria
- `DOWNLOAD` is claimed only by the download role
- `ANALYZE` is claimed only by the analysis/processing role
- `EXPORT` is claimed only by the export role
- local and production runtime configs reflect the new worker split
- docs explain why export is isolated and how operators should reason about the worker pools

## Notes
If naming needs to evolve from `processing-worker` toward `analyze-worker`, keep the migration explicit rather than mixing both meanings indefinitely.
