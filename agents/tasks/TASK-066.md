# TASK-066 Prepare And Execute `v1.0.0` Release

## Agent
infra-agent

## Summary
Turn the stabilized service into an intentional `v1.0.0` release with documented gates, release notes, and branch state.

## Context
After the service-level hardening work is complete, the project still needs a controlled release cut rather than an informal snapshot.

## Scope
- confirm the `v1.0.0` task gates and evidence are complete
- prepare release notes and known limitations
- execute the branch and version movement required by the project workflow
- tag or otherwise identify the release in source control
- update status docs so `main` and release documentation reflect reality

## Out of Scope
- post-release feature work
- speculative roadmap writing beyond `v1.0.0`
- bypassing incomplete gates

## Inputs
- backlog.md
- release-checklist.md
- TASK-064.md
- TASK-065.md
- agents/global/git-workflow.md

## Expected Deliverables
- release notes
- branch and version updates
- docs

## Constraints
- `v1.0.0` is a controlled release, not a best-effort snapshot
- do not cut the release with unresolved gate ambiguity
- keep the release record explicit and reproducible

## Acceptance Criteria
- the repository has a documented `v1.0.0` release point
- release notes and known limitations are written
- branch status docs match the released state
- the release decision is supported by documented evidence from prior tasks

## Notes
If any gate fails, this task should produce a clear no-go release packet instead of forcing the cut.
