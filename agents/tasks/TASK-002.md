# TASK-002 Bootstrap Python Worker

## Agent
worker-agent

## Summary
Create the initial Python worker skeleton for media processing.

## Context
The worker will later perform audio extraction, transcription, silence detection, and candidate generation.

## Scope
- create Python project structure
- add main entrypoint
- log startup message
- define folders for pipeline/services/models

## Out of Scope
- ffmpeg integration
- transcription
- backend communication
- candidate generation

## Inputs
- architecture
- worker role file

## Expected Deliverables
- runnable worker skeleton
- project structure

## Constraints
- Python 3.11+
- keep dependencies minimal
- no DB integration
- no API framework unless task explicitly needs it

## Acceptance Criteria
- worker starts from entrypoint
- startup log message is visible
- folders exist:
  - pipeline
  - services
  - models
