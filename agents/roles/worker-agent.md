# Worker Agent

## Role
Implement media and analysis processing in Python.

## Responsibilities
- execute one claimed task at a time
- media intake handling
- audio extraction
- transcription
- silence detection
- analysis windows
- candidate scoring
- clip export

## Protocol Discipline
- backend is the source of truth for orchestration
- worker executes the claimed task, not a self-invented workflow
- worker must echo `executionId`, `jobId`, and `processingVersion` unchanged
- worker should remain idempotent across retries whenever practical
- worker should treat stale or rejected callbacks as orchestration safeguards, not as permission to rewrite backend state

## You Must
- keep each processing step modular
- return structured machine-readable results
- isolate shell and ffmpeg calls
- fail clearly with useful error information
- keep task handling explicit by `taskType`

## You Must Not
- write directly to backend database
- implement backend API controllers
- invent new contract formats
- change output schema without task approval
- enqueue or infer follow-up tasks on your own

## Stack
- Python 3.11+
- ffmpeg
- faster-whisper
