# TASK-024 Implement Worker Clip Export

## Agent
worker-agent

## Summary
Implement final clip cutting in the worker using ffmpeg.

## Context
The MVP must produce a downloadable clip artifact from an approved candidate window.

## Scope
- add export service in the worker
- cut clip by start/end time using ffmpeg
- write the exported file to the configured artifact path
- return structured export result metadata

## Out of Scope
- backend download endpoint
- moderation workflow
- UI work
- advanced reframing or subtitle rendering

## Inputs
- architecture.md
- worker-protocol.md
- TASK-007.md

## Expected Deliverables
- export service
- ffmpeg wrapper
- structured result model
- tests or execution checks

## Constraints
- isolate shell interaction
- keep export behavior deterministic
- no direct DB writes

## Acceptance Criteria
- worker can cut a clip from a source video using candidate boundaries
- exported file is written to the expected artifact location
- result contains artifact path and status metadata
- ffmpeg failures are reported clearly
