# TASK-019 Implement Worker Candidate Analysis

## Agent
worker-agent

## Summary
Implement sliding-window metrics, scoring, overlap deduplication, and candidate generation in the worker.

## Context
The MVP must produce ranked clip candidates from transcript and silence signals without attempting advanced AI virality prediction.

## Scope
- calculate sliding windows over transcript and silence data
- compute speech density, silence ratio, emotion hits, and continuity score
- calculate total score using the MVP scoring formula
- deduplicate overlapping windows and select top candidates

## Out of Scope
- backend persistence
- moderation workflow
- export
- new scoring signals beyond the MVP plan

## Inputs
- worker-protocol.md
- data-models.md
- architecture.md

## Expected Deliverables
- analysis service
- scoring service
- candidate generation service
- tests or execution checks

## Constraints
- keep metrics limited to MVP signals
- output schema must remain stable
- no speculative ML additions

## Acceptance Criteria
- worker produces analysisWindows matching the contract
- worker produces deduplicated clipCandidates matching the contract
- total score is computed from the defined MVP signals
- top candidates are sorted by score
