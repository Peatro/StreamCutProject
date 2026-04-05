# TASK-033 Implement Worker Job Runner

## Agent
worker-agent

## Summary
Implement the worker-side orchestration runner that executes the processing pipeline for one claimed job.

## Context
The worker already has service modules for extraction, transcription, silence detection, analysis, and export, but it lacks a real pipeline coordinator that connects them into one job execution.

## Scope
- create a worker job runner service
- execute source intake or local source resolution
- run audio extraction, transcription, silence detection, and analysis in order
- build a final success payload matching the worker protocol

## Out of Scope
- backend result ingestion
- worker polling loop
- broker integration
- frontend changes

## Inputs
- worker-protocol.md
- TASK-014.md
- TASK-015.md
- TASK-017.md
- TASK-019.md

## Expected Deliverables
- code
- tests if practical

## Constraints
- keep pipeline steps modular
- return one structured machine-readable payload
- fail fast with clear stage-level errors

## Acceptance Criteria
- a claimed job can be processed through the existing worker services
- the runner produces a valid worker success payload
- failures include enough context for backend failure reporting
- runner logic stays separate from transport/polling concerns

## Notes
This task is the worker-side equivalent of stitching together existing modules into a usable pipeline.
