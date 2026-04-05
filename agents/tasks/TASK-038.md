# TASK-038 Run QA Release Validation

## Agent
qa-agent

## Summary
Validate the service against the MVP acceptance flow once the remaining execution tasks are complete.

## Context
After transport, processing, and export are wired, the project needs an explicit QA gate before any merge toward `main`.

## Scope
- run end-to-end validation on one uploaded file
- run end-to-end validation on one URL-based job
- verify failure behavior for at least one broken input or forced worker failure
- verify export flow and artifact accessibility

## Out of Scope
- feature development
- architecture changes
- performance tuning

## Inputs
- state-machine.md
- api-contracts.md
- worker-protocol.md
- TASK-026.md through TASK-037.md

## Expected Deliverables
- docs
- QA notes
- reproducible issue list if any failures are found

## Constraints
- focus on behavior, not style
- tie findings to acceptance criteria
- do not introduce new feature scope during validation

## Acceptance Criteria
- uploaded-file path is validated end-to-end
- URL-ingest path is validated end-to-end
- transcript, candidates, moderation, and export are validated on real data
- blocking failures are documented clearly enough to reopen the right task

## Notes
This task is the release gate for moving the current MVP contour toward `main`.
