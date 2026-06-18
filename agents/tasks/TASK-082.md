# TASK-082 Replace Native Delete Confirm With In-App Confirmation Dialog

## Agent
frontend-agent

## Summary
Job deletion (single, bulk, and job-detail) currently gates on the browser's native `window.confirm()`. Replace it with an in-app confirmation UI consistent with the existing dark design system, so destructive deletes use a styled, accessible confirm step instead of the OS dialog.

## Context
From real operator use (Obsidian `Problems.md`): "Не встроенное подтверждение удаления Job" — the delete confirmation is the native browser dialog, not part of the app. `app.js` calls `window.confirm(...)` in three places: `bindJobsPageActions` (single row delete, ~line 398), `bindBulkActions` (bulk delete, ~line 538), and `bindJobPageActions` (job-detail delete, ~line 602).

## Problem Frame
- Symptom: delete uses the OS-native confirm popup, inconsistent with the app UI.
- Suspected Layer: frontend only (`src/main/resources/static/app.js`, `styles.css`).
- Touched Contracts: none.
- Done Criterion: deleting a job (single/bulk/detail) shows an in-app styled confirmation; confirm proceeds with the existing delete flow, cancel aborts with no request.

## Scope
- Add a small reusable in-app confirmation helper in `app.js` (e.g. a promise-returning `confirmAction({title, message, confirmLabel})` that renders a modal/overlay and resolves true/false).
- Replace all three `window.confirm(...)` delete call sites with `await confirmAction(...)`, preserving the exact existing messages and downstream behavior (disable button, set interaction lock, call `api.deleteJob`, re-render, error banner).
- Style the dialog with existing design-system tokens in `styles.css` (reuse existing panel/button/overlay classes where present; do not invent a new palette).
- Keyboard: Enter confirms, Escape cancels; focus moves to the dialog; restore focus on close.

## Out of Scope
- No backend/API/contract changes.
- No change to what delete does, or to which statuses are deletable.
- Do not convert other `window.confirm`/`alert` usages unless they are the delete confirmations above.
- No new dependency or framework.

## Inputs
- `src/main/resources/static/app.js` (`bindJobsPageActions`, `bindBulkActions`, `bindJobPageActions`)
- `src/main/resources/static/styles.css`

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- Single-row, bulk, and job-detail delete each open the in-app confirmation instead of `window.confirm`.
- Confirm runs the existing delete path unchanged; cancel issues no request and leaves state untouched.
- Escape cancels, Enter confirms; the dialog is focus-managed and styled with the existing design system.
- No remaining `window.confirm` call for job deletion in `app.js`.

## Constraints
- Vanilla JS only, no framework, no new dependency (the React migration is deprioritized).
- Reuse existing CSS tokens/classes; match the Linear dark design system.
- No unrelated refactor of the surrounding handlers.

## Expected Deliverables
- code
