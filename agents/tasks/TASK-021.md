# TASK-021 Build Job List And Details UI

## Agent
frontend-agent

## Summary
Implement the MVP UI for browsing jobs and viewing job details.

## Context
Users need a minimal interface to inspect jobs, statuses, transcript preview, and candidate availability.

## Scope
- build job list page
- build job details page
- show job status, source, date, and duration
- show transcript preview, candidate summary, and job events sections

## Out of Scope
- candidate moderation actions
- export actions
- advanced filtering
- design-heavy polish

## Inputs
- api-contracts.md
- TASK-005.md
- TASK-008.md
- TASK-013.md
- TASK-020.md

## Expected Deliverables
- UI pages
- API integration
- basic empty/error states

## Constraints
- keep UI lightweight
- no frontend-only business logic
- use stable backend contracts

## Acceptance Criteria
- user can open a job list and navigate to job details
- job list shows core metadata and current status
- job details show transcript preview and related status information
- empty/error states are surfaced clearly
