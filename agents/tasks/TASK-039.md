# TASK-039 Define Upload Size Policy

## Agent
infra-agent

## Summary
Define a release-safe MVP upload policy so backend and UI work against one explicit set of constraints.

## Context
The local full-cycle MVP works, but realistic uploads currently fail at framework defaults with `413 Maximum upload size exceeded`. Release hardening must start by defining the intended policy instead of guessing.

## Scope
- define supported upload constraints for MVP
- define maximum file size
- define maximum request size
- define supported media formats for upload
- define expected user-facing behavior when limits are exceeded
- define whether current limits are temporary or intended

## Out of Scope
- implementing backend multipart handling
- redesigning upload flow
- chunked uploads or resumable uploads

## Inputs
- backlog.md
- runtime.md
- agents/global/workflow.md
- agents/contracts/api-contracts.md
- TASK-006.md
- TASK-038.md

## Expected Deliverables
- docs
- config guidance

## Constraints
- no architecture changes
- no speculative future-proofing
- keep policy practical for MVP local runtime

## Acceptance Criteria
- there is a single written source of truth for upload policy
- backend and UI work can proceed without guessing
- no ambiguous wording remains around upload size behavior

## Notes
This task should complete before backend and UI upload-limit implementation work begins.
