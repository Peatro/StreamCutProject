# TASK-086 Clip-Scoped Review Player With In-Clip Scrubbing

## Summary
The candidate review video plays the full source VOD and exposes the whole timeline, so the operator cannot scrub within just the clip. Scope the preview player to the candidate's [startSec, endSec] window — the scrub bar represents the clip, seeking is clamped to the clip, and the player area is sized sensibly for the review column.

## Agent
frontend-agent

## Context
From real operator use (Obsidian `Problems.md`): "Нужна возможность перемотки клипа (в области самого клипа отображается целиковый таймлайн)" and "Уменьшить размер области просмотра? -> Задел под область просмотра клипа в колонке справа". Each candidate renders `<video ... data-preview-video data-preview-start-sec data-preview-end-sec src="/api/jobs/{id}/source/stream">` and is wired by `bindCandidatePreviewPlayers`. The native `controls` timeline currently spans the entire source video, so scrubbing the clip is impractical.

## Problem Frame
- Symptom: clip preview shows the full-VOD timeline; no practical in-clip scrubbing; preview area sizing is off for the review column.
- Suspected Layer: frontend (`src/main/resources/static/app.js` candidate preview render + `bindCandidatePreviewPlayers`, `styles.css` `.candidate-preview*`).
- Touched Contracts: none (keeps the existing `/api/jobs/{id}/source/stream` source endpoint and start/end data attributes).
- Done Criterion: the preview scrubs within only the clip window — a custom clip-scoped scrub control whose 0–100% maps to [startSec, endSec], seeking clamped to that window — and the player area is appropriately sized in the review layout.

## Scope
- Replace reliance on the native full-length timeline with a clip-scoped scrubber for the preview: render a progress/seek control whose range maps to the candidate's `[startSec, endSec]`, update it as the clip plays, and let the operator seek within the clip (clamped so it cannot leave the window).
- Keep playback bounded to the clip window (already partially done via start/end attributes in `bindCandidatePreviewPlayers`); ensure play starts at `startSec`, stops/loops back at `endSec`, and the scrubber reflects position-within-clip.
- Size the preview area sensibly for the review column (the "reduce preview size / right-column groundwork" note): adjust `.candidate-preview-*` CSS so the player is compact and consistent; do not enlarge the page or break the existing candidate card layout.
- Preserve the existing "pause live updates while preview is playing/seeking" behavior (`getJobLivePauseReason` checks `video.paused`/`video.seeking`); ensure custom seeking still trips that pause guard.

## Out of Scope
- No backend/streaming/contract change; keep `/api/jobs/{id}/source/stream` and the start/end data attributes.
- No clip trimming/editing, no re-encode, no server-side clip extraction for preview.
- No change to export/download.
- Do not move the player into a separate persistent right-hand panel/route — only size it for the current review column (the "задел"/groundwork, not a layout rebuild).
- No new dependency or framework.

## Inputs
- `src/main/resources/static/app.js` (candidate preview markup in `renderCandidates`, `bindCandidatePreviewPlayers`, `getJobLivePauseReason`)
- `src/main/resources/static/styles.css` (`.candidate-preview`, `.candidate-preview-shell`, `.candidate-preview-stage`, `.candidate-preview-foot`, `.candidate-preview-progress`)

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- The preview scrub control represents only the clip window; its full range corresponds to `[startSec, endSec]`.
- Seeking via the control stays clamped within the clip; playback starts at `startSec` and does not run past `endSec`.
- The preview area is visibly more compact/appropriately sized in the review column without breaking the candidate card layout.
- Live updates still pause while a preview is actively playing or being scrubbed.
- Works for multiple candidate cards on the page independently.

## Constraints
- Vanilla JS + CSS only, no framework, no new dependency.
- Reuse existing design-system tokens/classes; match the Linear dark design system.
- Must coexist with TASK-085's in-place candidate updates (do not assume a full re-render rebinds players on every action).
- No unrelated refactor.

## Expected Deliverables
- code
