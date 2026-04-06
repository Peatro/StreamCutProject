# TASK-065 Write Operator Runbook, Backup Restore, And Upgrade Notes

## Agent
infra-agent

## Summary
Document how to run, recover, back up, restore, and upgrade the service without tribal knowledge.

## Context
A finished service needs operating instructions. Right now the project has runtime notes, but not a cohesive operator runbook.

## Scope
- write an operator runbook for normal service operation
- document backup and restore expectations for database and object storage
- document upgrade and rollback notes for runtime and schema changes
- document routine maintenance tasks such as cleanup verification and health checks
- keep the docs aligned with the actual deployed runtime shape

## Out of Scope
- fully automated disaster-recovery system
- enterprise compliance documentation
- cloud-provider-specific procedures unless the chosen deployment requires them

## Inputs
- runtime.md
- TASK-057.md
- TASK-058.md
- TASK-059.md
- TASK-062.md
- TASK-063.md

## Expected Deliverables
- docs

## Constraints
- runbooks must match reality, not aspirational architecture
- prefer short operational procedures over vague prose
- document preconditions and failure modes clearly

## Acceptance Criteria
- operators can start, check, back up, restore, and upgrade the service from the written docs
- known operational risks and limitations are explicit
- recovery steps do not depend on undocumented repository archaeology
- the runbook is concrete enough to be exercised by `TASK-067` without inventing missing steps

## Notes
If any procedure is intentionally manual for `v1.0.0`, document that explicitly instead of implying automation that does not exist.
