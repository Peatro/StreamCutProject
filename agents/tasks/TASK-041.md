# TASK-041 Expose Upload Constraints In UI

## Agent
frontend-agent

## Summary
Surface upload constraints and oversize upload errors clearly in the UI so users do not discover them only after failure.

## Context
The upload path needs explicit user guidance once backend multipart limits are defined. The current MVP should make limits visible before submit and should explain oversize failures without generic error noise.

## Scope
- show allowed file size and supported formats in upload UI
- show a clear error message for `413` or size-limit failures
- keep wording compact and practical
- make retry path obvious

## Out of Scope
- full page redesign
- upload flow redesign
- new frontend stack or build tooling

## Inputs
- TASK-039.md
- TASK-040.md
- src/main/resources/static/index.html
- src/main/resources/static/app.js
- src/main/resources/static/styles.css

## Expected Deliverables
- code
- docs if UI behavior changes materially

## Constraints
- preserve current UI architecture
- focus only on upload clarity
- do not introduce speculative UI state machinery

## Acceptance Criteria
- users can see upload constraints before submitting
- oversize upload failures show a clear explanation
- the UI avoids generic low-signal failure messaging

## Notes
Prefer concise copy over verbose instructional text.
