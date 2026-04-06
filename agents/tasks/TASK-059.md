# TASK-059 Add Health, Readiness, And Worker Diagnostics

## Agent
backend-agent

## Summary
Give operators clear signals about backend readiness, worker presence, and job-processing health.

## Context
The service currently exposes only a minimal health signal. `v1.0.0` needs diagnostics that help operators distinguish "app is up" from "service is actually ready and workers are alive."

## Scope
- add readiness and liveness signals suitable for the deployed runtime
- surface worker heartbeat or equivalent recent-activity diagnostics
- expose enough queue or processing diagnostics to detect stuck processing without database inspection
- document how operators should interpret the new signals
- add focused tests where practical

## Out of Scope
- full tracing platform
- custom monitoring UI
- deep infrastructure dashboards

## Inputs
- TASK-048.md
- TASK-057.md
- agents/contracts/state-machine.md
- src/main/java/com/peatroxd/streamcutproject
- worker

## Expected Deliverables
- code
- tests where appropriate
- docs

## Constraints
- diagnostics must be operationally useful, not decorative
- keep the signal set small and explainable
- do not require direct database access for normal operator checks

## Acceptance Criteria
- operators can tell whether the backend is ready to serve traffic
- operators can tell whether a worker has been active recently
- obvious stuck-processing conditions are diagnosable without archaeology
- docs explain the meaning of each health and readiness signal

## Notes
If Actuator or another framework feature is introduced, keep the exposed surface intentional and documented.
