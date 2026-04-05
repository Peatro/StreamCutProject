# TASK-036 Add Artifact Access And UI Runtime Controls

## Agent
frontend-agent

## Summary
Extend the UI so operators can follow real processing state, refresh data, and access exported artifacts directly.

## Context
The UI now supports basic job creation and moderation actions, but a full service needs clearer runtime visibility and artifact access once end-to-end processing is live.

## Scope
- surface real processing state transitions more clearly
- show export artifact availability and access links
- add practical refresh/status controls where needed
- ensure job details remain usable when transcript and candidate data arrive asynchronously

## Out of Scope
- backend API redesign
- major visual redesign
- advanced filtering or search
- video player embedding beyond what MVP requires

## Inputs
- api-contracts.md
- TASK-021.md
- TASK-022.md
- TASK-035.md

## Expected Deliverables
- code
- docs if user behavior changes materially

## Constraints
- keep UI lightweight
- use existing backend contracts
- do not hide important processing failures

## Acceptance Criteria
- operators can see when a job is queued, processing, ready for review, exporting, completed, or failed
- exported artifacts are visible and accessible from the UI when available
- refresh and runtime control points are present where the current workflow needs them
- UI remains functional on desktop and mobile

## Notes
Preserve the existing visual language unless a small usability fix requires otherwise.
