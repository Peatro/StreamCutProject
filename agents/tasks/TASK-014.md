# TASK-014 Implement Worker Audio Extraction

## Agent
worker-agent

## Summary
Implement the worker pipeline step that extracts audio from an input video file.

## Context
Audio extraction is the first processing step required before transcription and silence detection.

## Scope
- add an isolated audio extraction service
- encapsulate ffmpeg invocation
- return structured result with produced audio path
- fail with useful error information

## Out of Scope
- transcription
- silence detection
- backend callbacks
- clip export

## Inputs
- architecture.md
- worker-agent.md
- worker-protocol.md

## Expected Deliverables
- worker service
- ffmpeg wrapper
- structured result model
- tests or execution checks

## Constraints
- isolate shell interaction
- keep output machine-readable
- no direct DB writes

## Acceptance Criteria
- worker can extract audio from a supplied video path
- output includes the produced audio file path
- ffmpeg failures are surfaced clearly
- extraction logic is modular and testable
