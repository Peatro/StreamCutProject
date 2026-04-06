# TASK-054 Fix Stale Artifact Semantics After Failed Export

## Agent
backend-agent

## Summary
Ensure the current export endpoints never serve a stale clip as if the latest export attempt succeeded.

## Context
`TASK-045` confirmed a release blocker: after a successful export, a later failed retry can still leave `/api/exports/{candidateId}/file` and `/stream` returning `200` from the old artifact. That is ambiguous and not acceptable for a clean release or service baseline.

## Scope
- define the canonical semantics for current export state versus historical artifacts
- make `/api/exports/{candidateId}`, `/file`, and `/stream` reflect the latest export attempt consistently
- prevent failed export retries from exposing an old clip as the current result
- update tests and docs to match the chosen semantics

## Out of Scope
- full export history browser
- multi-version artifact retention UI
- storage-provider redesign

## Inputs
- TASK-045.md
- release-checklist.md
- agents/contracts/state-machine.md
- src/main/java/com/peatroxd/streamcutproject

## Expected Deliverables
- code
- tests
- docs

## Constraints
- preserve the current storage architecture
- keep API behavior explicit and stable
- do not silently redefine export success semantics in the UI only

## Acceptance Criteria
- the latest export attempt is the source of truth for current export endpoints
- failed export retries do not leave `/file` or `/stream` returning a stale success for the current candidate state
- successful completed exports remain downloadable
- release docs explicitly state the resulting behavior

## Notes
Deleting the old artifact, versioning attempts, or explicitly invalidating current access are all acceptable if the behavior is coherent and documented.
