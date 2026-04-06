# TASK-042 Browser QA Pass For Core Happy Path

## Agent
qa-agent

## Summary
Run a real browser walkthrough of the live Docker stack to validate the core happy path from landing page to exported artifact.

## Context
API and Docker validation already exist, but release hardening still requires a browser-based operator walkthrough against the real running UI.

## Scope
- verify landing page
- verify job list page
- verify job details page
- verify transcript visibility
- verify candidate review UI
- verify approve flow
- verify export flow
- verify artifact download flow
- verify upload flow in a real browser

## Out of Scope
- feature development
- visual redesign
- architecture changes

## Inputs
- backlog.md
- runtime.md
- TASK-041.md
- agents/contracts/api-contracts.md

## Expected Deliverables
- docs
- QA notes
- actionable defect list if needed

## Constraints
- focus on actual user flow, not styling opinions
- document reproduction steps for defects
- do not introduce new feature scope during validation

## Acceptance Criteria
- full happy-path browser walkthrough is completed end-to-end
- defects are documented with reproduction steps
- no major UX blocker remains undiscovered

## Notes
Screenshots are optional and only needed when they improve issue clarity.
