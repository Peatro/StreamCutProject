# TASK-015 Implement Worker Transcription

## Agent
worker-agent

## Summary
Implement transcription in the Python worker using faster-whisper.

## Context
The MVP uses transcript segments as a primary signal for review and scoring.

## Scope
- integrate faster-whisper in the worker
- transcribe extracted audio
- map results to transcript segment output schema
- include language and duration metadata in the result

## Out of Scope
- transcript persistence in backend
- silence detection
- candidate scoring
- UI work

## Inputs
- worker-protocol.md
- architecture.md
- TASK-014.md

## Expected Deliverables
- transcription service
- output mapping
- result model
- tests or execution checks

## Constraints
- keep output schema stable
- return JSON-compatible data
- no backend database access

## Acceptance Criteria
- worker produces transcriptSegments matching the contract
- language metadata is included in the result
- transcription failures are reported clearly
- implementation remains isolated from backend internals
