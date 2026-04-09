# TASK-071 Move Artifact Delivery To Signed URLs And Reduce Backend Media Proxying

## Agent
backend-agent

## Summary
Shift artifact delivery away from backend-streamed media responses toward signed URLs and a cleaner storage-backed delivery model.

## Context
The current service can stream source and export media through Spring controllers. That is acceptable for MVP validation, but it is not the desired long-term architecture. Backend should remain the control plane, not the default media proxy.

## Scope
- expose signed URL delivery for completed export artifacts as the primary delivery mechanism
- reduce or retire backend-streamed artifact endpoints where the storage contract makes that safe
- define the intended handling of source-media access instead of leaving it implicit
- update UI or API responses as needed so clients can consume the new delivery contract
- document the delivery model and any remaining exceptions

## Out of Scope
- CDN rollout
- public anonymous media sharing
- adaptive streaming features

## Inputs
- TASK-070.md
- TASK-058.md
- Documentation/backlog.md
- src/main/java/com/peatroxd/streamcutproject/storage
- src/main/java/com/peatroxd/streamcutproject/clipcandidate
- src/main/resources/static

## Expected Deliverables
- code
- tests where appropriate
- docs

## Constraints
- do not break current operator flows without a documented replacement path
- keep artifact access policy explicit and access-controlled
- do not turn the backend into a permanent compatibility proxy for large media unless there is a stated exception

## Acceptance Criteria
- completed export artifacts are retrievable through signed URLs with documented TTL behavior
- the preferred client flow no longer depends on backend streaming of large export files
- source-media handling is documented explicitly rather than implied
- UI and API behavior match the documented delivery contract

## Notes
If some backend-streaming endpoint must remain temporarily for operator convenience, document it as a deliberate exception rather than leaving the architecture ambiguous.
