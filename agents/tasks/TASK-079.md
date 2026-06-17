# TASK-079 Job Lifecycle On Moderation: Auto-Complete, Manual Complete, And Deletable-From-Review

## Agent
backend-agent

## Summary
Make a job leave `READY_FOR_REVIEW` correctly: auto-complete when nothing is left to do, allow a manual "complete now" action, and allow deleting/clearing a job that is still in review.

## Context
Operator real-use problem #3: after approving or rejecting clips, the job stays in `READY_FOR_REVIEW` forever. Root cause: `VodJobService.moderateCandidate` (~line 1422) only sets the candidate's `moderationStatus`; it never re-evaluates or transitions the job status. Related problems #2/#4: jobs in `READY_FOR_REVIEW` cannot be deleted because `DELETABLE_STATUSES` (line 136) is only `{COMPLETED, FAILED, CANCELED}`, so the operator cannot clear the review backlog.

Decision (operator): two completion paths — an automatic rule (combine "all moderated" + "all approved exported") and a manual override button.

## Problem Frame
- Symptom: job is stuck in `READY_FOR_REVIEW` after moderation; cannot be cleared.
- Suspected Layer: `VodJobService` moderation/export/delete logic and `DELETABLE_STATUSES`.
- Touched Contracts: adds one operator endpoint (`POST /api/jobs/{id}/complete`); existing DELETE endpoint behavior widened to allow `READY_FOR_REVIEW`.
- Done Criterion: a fully-resolved job auto-completes; an operator can force-complete; a review job can be deleted.

## Scope
- **Auto-complete rule (combined):** after a candidate is moderated AND after an export completes, re-evaluate the job. If the job is in `READY_FOR_REVIEW` and there are no remaining `PENDING` candidates and every `APPROVED` candidate has `ExportStatus.COMPLETED`, transition the job to `COMPLETED`.
  - A job whose candidates are all `REJECTED` (none approved, none pending) → `COMPLETED` (nothing left to export).
  - An `APPROVED` candidate not yet exported keeps the job in `READY_FOR_REVIEW`; completion can therefore also fire from the export-completion path (~line 955 where `ExportStatus.COMPLETED` is set).
  - Emit a job event and reuse the existing `METRIC_JOBS_COMPLETED` counter on transition.
- **Manual complete:** add `POST /api/jobs/{id}/complete` that force-transitions a job from `READY_FOR_REVIEW` to `COMPLETED` (operator decides it is done regardless of remaining pending candidates). Reject with `409` if the job is not in `READY_FOR_REVIEW`. Emit a job event.
- **Deletable from review:** add `READY_FOR_REVIEW` to `DELETABLE_STATUSES` so a job in review can be deleted via the existing `DELETE /api/jobs/{id}` (which already cleans artifacts and related rows). Do not change the existing artifact/row cleanup logic.

## Out of Scope
- No frontend work (buttons live in TASK-078, which consumes the endpoints/behavior here).
- No bulk-delete endpoint (TASK-078 can loop the existing single delete; only add one if the agent finds looping clearly inadequate — default is no new endpoint).
- No change to how candidates are exported or moderated beyond the post-action job re-evaluation.
- Do not make actively-processing states (DOWNLOADING, TRANSCRIBING, etc.) deletable.

## Inputs
- `src/main/java/.../vodjob/VodJobService.java` (`moderateCandidate` ~1422, export completion ~955, `deleteJob` 414, `DELETABLE_STATUSES` 136, `METRIC_JOBS_COMPLETED`)
- `src/main/java/.../vodjob/VodJobController.java` (where to add the complete endpoint)
- `src/main/java/.../clipcandidate/ModerationStatus.java`, `ExportStatus.java`
- `src/main/java/.../vodjob/JobStatus.java`
- existing tests under `src/test/.../vodjob/`

## Touched Contracts
- new endpoint `POST /api/jobs/{id}/complete`
- `DELETE /api/jobs/{id}` now also accepts `READY_FOR_REVIEW`

## Schema Impact
- none

## Operational Risk
- low — status transitions only; deletion cleanup path is unchanged and already gated.

## Rollback Or Migration Note
- none

## Expected Deliverables
- code
- tests

## Constraints
- use existing stack and patterns (Spring, existing service/repo structure); no new dependency
- no unrelated refactor
- the auto-complete evaluation must be idempotent and only act on jobs currently in `READY_FOR_REVIEW`
- keep CSRF/auth behavior identical to the other `/api/jobs` endpoints

## Acceptance Criteria
- moderating the last `PENDING` candidate (no approved-but-unexported left) transitions the job to `COMPLETED`
- a job with an approved-but-not-yet-exported candidate stays `READY_FOR_REVIEW` until that export completes, then transitions to `COMPLETED`
- a job with all candidates rejected transitions to `COMPLETED`
- `POST /api/jobs/{id}/complete` transitions a `READY_FOR_REVIEW` job to `COMPLETED` and returns `409` from any other status
- `DELETE /api/jobs/{id}` succeeds for a `READY_FOR_REVIEW` job and still cleans up artifacts and related rows
- tests cover: auto-complete on last moderation, stays-then-completes via export, all-rejected, manual complete (happy + 409), delete from review

## Notes
- This pairs with TASK-078 (frontend buttons). Land the backend behavior first so the UI can call it.
