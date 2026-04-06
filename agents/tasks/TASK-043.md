# TASK-043 Fix Core UI Friction Found During Browser QA

## Agent
frontend-agent

## Summary
Resolve the highest-signal UI blockers discovered during the browser QA happy-path pass.

## Context
Browser QA should surface practical operator issues such as unclear statuses, broken actions, weak artifact access, or missing error visibility. This task exists to fix those findings without turning into a broad redesign.

## Scope
- fix only high-signal browser QA findings
- prioritize broken actions
- prioritize confusing status display
- prioritize missing error visibility
- prioritize unclear artifact access
- prioritize weak candidate action affordance

## Out of Scope
- visual rewrite
- speculative polish unrelated to QA findings
- backend architecture changes

## Inputs
- TASK-042.md
- DESIGN.md
- src/main/resources/static

## Expected Deliverables
- code
- short changelog of fixes

## Constraints
- follow existing design direction
- keep fixes focused on concrete QA findings
- do not broaden scope without explicit need

## Acceptance Criteria
- major browser QA blockers are removed
- the happy path is understandable without developer interpretation
- no critical click path remains confusing or broken

## Notes
This task should consume actual findings from `TASK-042`, not hypothetical UI improvements.
