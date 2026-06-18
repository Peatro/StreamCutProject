# TASK-085 Update Moderated Candidate In Place Instead Of Full Job-Page Re-Render

## Summary
Approving or rejecting a single candidate currently re-renders the entire job page, which reloads every candidate card (and their preview videos) and makes the page lag. Update only the moderated candidate's state in place so a single approve/reject does not rebuild the whole page.

## Agent
frontend-agent

## Context
From real operator use (Obsidian `Problems.md`): "После аппрува или реджекта клипа происходит перезагрузка всех клипов на странице / Страница пролагивает". In `app.js`, `bindCandidateActions` calls `renderJobPage(root, jobId, ...)` after approve/reject, which does `root.innerHTML = ...` for the whole page — destroying and recreating all candidate cards and their `<video>` preview elements every time. The backlog already records that full-`innerHTML` re-render is the known cost here and that the fix is targeted DOM patching, not a React migration.

## Problem Frame
- Symptom: approve/reject one clip reloads all clips and lags the page.
- Suspected Layer: frontend (`src/main/resources/static/app.js`, `bindCandidateActions` and candidate render/update path).
- Touched Contracts: none (same `approveCandidate`/`rejectCandidate` API and response).
- Done Criterion: approving/rejecting one candidate updates only that card (status pill, disabled buttons, moderator note, runtime state) without rebuilding sibling cards or their video elements, and without a visible page reflow.

## Scope
- After a successful approve/reject, patch only the affected `[data-candidate-card]` in place: update its moderation status pill, enable/disable the Approve/Reject buttons to match the new status, and refresh the per-candidate runtime/note text — instead of calling `renderJobPage`.
- Keep the live snapshot consistent so the next poll does not immediately full-re-render and undo the in-place update (update `liveUpdates.snapshot` / the moderated candidate's tracked fields, or otherwise reconcile so the snapshot diff does not flip).
- Preserve existing success/error banner behavior and the interaction lock.
- If the moderation changes job-level status (e.g. auto-complete from TASK-079), let the existing live refresh pick that up on its normal cadence; do not block on it.

## Out of Scope
- No backend/contract/API change.
- No change to keyboard shortcuts, pagination, or export/download flows beyond what's needed to reflect the new status on the same card.
- Do not rewrite the whole render pipeline or introduce a virtual DOM / framework — targeted DOM patching only.
- No change to TASK-084's worker-runtime panel.

## Inputs
- `src/main/resources/static/app.js` (`bindCandidateActions`, `renderCandidates`, candidate card markup, `buildJobPageSnapshot`, live refresh)

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- Approving or rejecting a candidate updates only that candidate's card; other cards and their `<video>` previews are not recreated.
- The moderated card shows the new status pill and correct enabled/disabled Approve/Reject buttons immediately.
- The subsequent live poll does not trigger a full-page re-render solely because of the just-applied moderation change.
- Success/error banners and the interaction lock still behave as before.

## Constraints
- Vanilla JS only, no framework, no new dependency (React migration is deprioritized; this task is the targeted-DOM-patch alternative).
- Keep the change scoped to candidate moderation; do not refactor unrelated rendering.
- No regression to clip preview players currently bound by `bindCandidatePreviewPlayers`.

## Expected Deliverables
- code
