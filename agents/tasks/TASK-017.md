# TASK-017 Implement Worker Silence Detection

## Agent
worker-agent

## Summary
Implement silence detection in the worker using ffmpeg silencedetect output.

## Context
Silence data is one of the core MVP signals for analysis window scoring.

## Scope
- add a silence detection service
- encapsulate ffmpeg silencedetect invocation
- parse detected silence intervals
- map parsed results to contract output

## Out of Scope
- backend persistence
- transcript generation
- candidate scoring
- export

## Inputs
- worker-protocol.md
- architecture.md
- TASK-014.md

## Expected Deliverables
- worker service
- parser
- structured output model
- tests or execution checks

## Constraints
- isolate shell interaction
- output must stay JSON-compatible
- no DB writes

## Acceptance Criteria
- worker produces silenceSegments matching the contract
- parser handles multiple silence intervals correctly
- ffmpeg errors are surfaced clearly
- implementation is modular and reusable
