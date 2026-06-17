# TASK-078 Job List/Detail UI: Static Progress At Rest, Delete, Clear, And Complete

## Agent
frontend-agent

## Summary
Fix the operator UI so the pipeline progress bar stops animating when a job is at rest, and add job management actions: delete a job, bulk/selective clear the list, and a manual "complete" action.

## Context
Operator real-use problems from the Obsidian `Problems.md`:
1. The "OVERALL PIPELINE" progress bar keeps showing the moving/indeterminate gradient even when the job is done and in `READY_FOR_REVIEW` (status "Idle", 100%). It should render static/filled at rest.
2. There is no way to bulk or selectively clear the job list.
4. There is no way to delete a job from the UI — even though the backend `DELETE /api/jobs/{id}` is fully implemented.

This task is the frontend half. The backend behavior it depends on is TASK-079 (job auto/manual completion + allowing delete from `READY_FOR_REVIEW` + the `POST /api/jobs/{id}/complete` endpoint). Land TASK-079 first.

## Problem Frame
- Symptom: animated progress bar at rest; no delete/clear/complete controls in the UI.
- Suspected Layer: static frontend (`src/main/resources/static/app.js`, `styles.css`, `index.html`, `job.html`).
- Touched Contracts: consumes existing `DELETE /api/jobs/{id}` and the new `POST /api/jobs/{id}/complete` (TASK-079).
- Done Criterion: bar is static when the job is not actively processing; operator can delete, clear, and complete jobs from the UI.

## Scope
- **Static progress at rest (#1):** when a job is not in an actively-processing status (i.e. terminal/at-rest states such as `READY_FOR_REVIEW`, `COMPLETED`, `FAILED`, `CANCELED`), render the pipeline bar as a static filled (or empty for failed/canceled) bar — remove the animated/indeterminate gradient. Keep the animation only while a worker stage is actually running. Applies to both the job list (`index.html`) and job detail (`job.html`) wherever the bar is rendered.
- **Delete a job (#4):** add a per-job delete control that calls `DELETE /api/jobs/{id}` (with the existing CSRF + auth flow used by other mutating calls in `app.js`). Confirm before deleting. Remove the job from the list / navigate away from detail on success; surface a clear error if the backend rejects (e.g. `409` for non-deletable status).
- **Bulk/selective clear (#2):** allow selecting multiple jobs (or a "clear all deletable") and deleting them, looping the existing single `DELETE` endpoint. Only offer deletion for jobs in deletable statuses.
- **Manual complete:** add a "Complete" / "Mark done" action on a `READY_FOR_REVIEW` job that calls `POST /api/jobs/{id}/complete` (TASK-079), then refreshes the view.

## Out of Scope
- No backend changes (covered by TASK-079).
- No framework migration — keep the existing vanilla HTML/JS/CSS approach (no React; that is the separate, deferred TASK-075).
- No redesign of the page beyond adding these controls and fixing the bar state.
- No new dependency.

## Inputs
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/index.html`
- `src/main/resources/static/job.html`
- TASK-079 (endpoints/behavior this consumes)

## Touched Contracts
- consumes `DELETE /api/jobs/{id}` and `POST /api/jobs/{id}/complete`

## Schema Impact
- none

## Operational Risk
- low — UI-only; mutating actions reuse existing authenticated/CSRF call patterns.

## Rollback Or Migration Note
- none

## Expected Deliverables
- code

## Constraints
- match the existing design system and the existing CSRF/auth fetch pattern in `app.js`
- keep it vanilla (no React, no new dependency, no build step beyond what exists)
- destructive actions (delete, clear) must confirm first
- do not animate the progress bar when no worker stage is running

## Acceptance Criteria
- at `READY_FOR_REVIEW`/`COMPLETED` the pipeline bar is static (no moving gradient); it animates only while a stage is actively running
- a job can be deleted from the UI; the list updates and errors are surfaced
- multiple jobs can be cleared in one operator action (looping the existing delete), limited to deletable statuses
- a `READY_FOR_REVIEW` job can be completed from the UI via the complete endpoint, and the view reflects the new status
- behavior degrades gracefully if a delete/complete call is rejected (clear message, no broken UI state)

## Notes
- Depends on TASK-079. If TASK-079 is not yet merged, the delete-from-review and complete actions will fail against the backend — sequence accordingly.
