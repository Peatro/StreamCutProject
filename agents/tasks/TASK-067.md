# TASK-067 Run Backup Restore And Rollback Drill

## Agent
qa-agent

## Summary
Exercise the written backup, restore, and rollback procedures against the chosen runtime so `v1.0.0` does not rely on untested recovery docs.

## Context
Runbooks are necessary, but they are not enough on their own. Before release, the project should prove that restore and rollback procedures actually work in practice.

## Scope
- execute the documented backup procedure
- restore the service from backup into a clean runtime
- verify the restored service can serve expected operator flows
- execute at least one realistic rollback or downgrade-safe recovery procedure if the release workflow requires it
- record defects or missing documentation discovered during the drill

## Out of Scope
- high-availability architecture
- disaster-recovery across regions
- fully automated backup platform rollout

## Inputs
- TASK-065.md
- TASK-057.md
- TASK-058.md
- runtime.md
- release-checklist.md

## Expected Deliverables
- QA notes
- docs updates if gaps are found

## Constraints
- run the drill against the actual chosen runtime shape, not a simplified toy variant
- keep the drill reproducible and documented
- treat missing or ambiguous operator steps as defects, not acceptable improvisation

## Acceptance Criteria
- backup and restore are exercised successfully on a clean runtime
- restored data is sufficient to operate the service on expected core flows
- rollback or release recovery steps are documented and validated to the extent supported by the release model
- any gaps found during the drill are fixed or explicitly accepted before `TASK-066`

## Notes
If the release model does not support binary rollback, document the real recovery procedure instead of pretending rollback exists.
