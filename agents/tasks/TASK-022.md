# TASK-022 Build Candidate Review UI

## Agent
frontend-agent

## Summary
Implement the MVP UI for candidate moderation and export initiation.

## Context
Manual review is a core MVP workflow and must stay lightweight and operational.

## Scope
- render candidate list for a job
- show start/end, score, and transcript excerpt
- add approve and reject actions
- expose export action entry point and status feedback

## Out of Scope
- clip player enhancements
- advanced sorting/filtering
- redesign of backend contracts
- final export file download handling

## Inputs
- api-contracts.md
- TASK-020.md
- TASK-023.md

## Expected Deliverables
- moderation UI
- API integration
- action state handling

## Constraints
- keep interactions simple
- errors must be visible
- avoid adding frontend business rules

## Acceptance Criteria
- user can approve and reject a candidate from the UI
- candidate metadata is displayed clearly
- export action can be triggered from the review UI
- action success and failure states are visible
