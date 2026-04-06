# TASK-051 Prepare MVP Release Checklist

## Agent
infra-agent

## Summary
Create a concrete MVP release checklist so movement from `develop` to `main` depends on explicit gates rather than memory.

## Context
The project now needs release discipline. A written checklist should define QA gates, docs gates, limitations, and merge readiness.

## Scope
- define release checklist for `develop` to `main`
- include required QA gates
- include required docs gates
- include known limitations section
- include rollback or recovery notes if relevant

## Out of Scope
- executing the release merge itself
- adding new feature scope
- automated release tooling

## Inputs
- backlog.md
- TASK-042.md through TASK-050.md
- agents/global/git-workflow.md

## Expected Deliverables
- docs

## Constraints
- keep checklist practical and reviewable
- do not encode unverified assumptions as release gates
- make required evidence explicit

## Acceptance Criteria
- there is a written checklist for MVP release
- release readiness no longer depends on memory or mood
- merge gates are clear enough for orchestrator-driven execution

## Notes
This is a process task, not an implementation task.

## Observed On 2026-04-06
- `release-checklist.md` is now the source-of-truth gate document for `develop` to `main`.
- The checklist explicitly requires `TASK-042` through `TASK-049` to be addressed before release movement.
- `TASK-044` remains a blocker until the URL ingest failure path is fixed or formally accepted.
- No application logic changes were needed for this task.
