# TASK-084 Stabilize Processing Status Labels And Surface Percent

## Agent
frontend-agent

## Summary
During processing, the status/phase text in the job UI jumps and reflows as labels swap each poll. Stabilize the worker-runtime label area so text does not visibly jitter between live refreshes, and make the percentage the steady, prominent signal of progress.

## Context
From real operator use (Obsidian `Problems.md`): "Перескакивающие надписи при обработке видео (Отображать проценты?)". The job page re-renders via full `root.innerHTML` on every live poll (`renderJobPage`), and `renderWorkerRuntimePanel` rebuilds phase label, progress message, stage-progress text, and heartbeat badge each time. Layout shifts because these strings change length/line-count between polls. Progress percent already exists (`normalizedProgressPercent`, the `worker-progress-pill`).

## Problem Frame
- Symptom: processing labels jump/reflow each poll; progress is hard to read at a glance.
- Suspected Layer: frontend (`src/main/resources/static/app.js` `renderWorkerRuntimePanel` / live refresh, `styles.css`).
- Touched Contracts: none.
- Done Criterion: during active processing the label area holds a stable layout (no visible jump) across polls, and the percent is the clear primary progress indicator.

## Scope
- Stabilize the worker-runtime label/progress area against per-poll reflow: reserve stable space (fixed min-height / stable line count) for the phase label, progress copy, and stage-progress row so changing text does not shift surrounding layout.
- Make the percentage prominent and steady (e.g. keep the percent pill stable and visually primary).
- Prefer updating the affected text/width nodes in place during the live refresh instead of relying solely on full-panel `innerHTML` swap, IF it can be done within this panel without a broader refactor; otherwise achieve stability purely via CSS reserved space. Do not rewrite the whole live-refresh architecture.

## Out of Scope
- No backend/contract change; consume existing `progressPercent` / `progressMessage` / status fields as-is.
- No change to polling cadence or to `buildJobPageSnapshot` semantics.
- Not a full move off `innerHTML` re-rendering for the whole page (that is TASK-085's concern for candidates) — limit in-place updates to the worker-runtime panel if attempted.
- No new dependency or framework.

## Inputs
- `src/main/resources/static/app.js` (`renderWorkerRuntimePanel`, `describeStageProgress`, `normalizedProgressPercent`, live-refresh path)
- `src/main/resources/static/styles.css` (`.worker-progress-*`, `.worker-runtime-*`)

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- During active processing, the phase/progress label area does not visibly jump or reflow surrounding content between polls.
- The progress percentage is rendered as the stable, primary progress signal.
- No regression to the at-rest (terminal/READY_FOR_REVIEW) presentation introduced by TASK-078's static bar.
- Behavior verified across at least two consecutive status transitions (e.g. DOWNLOADING → TRANSCRIBING).

## Constraints
- Vanilla JS + CSS only, no framework, no new dependency.
- Reuse existing design-system tokens/classes.
- No unrelated refactor; keep the change inside the worker-runtime panel + its CSS.

## Expected Deliverables
- code
