# TASK-034 Complete Candidate Generation Pipeline

## Agent
worker-agent

## Summary
Complete the worker candidate-generation pipeline so that real jobs yield reviewable clip candidates and excerpts.

## Context
Core analysis modules exist, but the service still needs a coherent final candidate-generation stage tied to real processing outputs and the target `READY_FOR_REVIEW` state.

## Scope
- verify and complete sliding-window analysis wiring
- generate top candidate windows from analysis results
- deduplicate overlapping candidate ranges
- build transcript excerpts for produced candidates

## Out of Scope
- backend persistence
- moderation API
- export execution
- UI changes

## Inputs
- worker-protocol.md
- data-models.md
- TASK-019.md
- TASK-033.md

## Expected Deliverables
- code
- tests if practical

## Constraints
- keep scoring logic explicit, not heuristic sprawl
- output schema must stay aligned with worker protocol
- avoid adding speculative AI ranking features

## Acceptance Criteria
- pipeline produces deterministic candidate output from real analysis results
- overlapping windows are reduced into a usable candidate set
- each candidate includes transcript excerpt text
- output is ready for backend ingestion and review UI

## Notes
Stay within the scoring model already documented for MVP.
