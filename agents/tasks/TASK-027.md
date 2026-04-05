# TASK-027 Persist Uploaded Source Files

## Agent
backend-agent

## Summary
Persist uploaded source files into storage and record their resolved paths on the job.

## Context
Uploaded jobs currently store only the original filename. A real processing pipeline requires a concrete stored source artifact that the worker can consume later.

## Scope
- save uploaded files through the storage abstraction
- assign stable source-video storage paths
- write storage metadata to `vod_job`
- preserve original filename separately from resolved storage path

## Out of Scope
- automatic worker dispatch
- URL download handling
- audio extraction
- transcript generation

## Inputs
- data-models.md
- api-contracts.md
- TASK-006.md
- TASK-007.md

## Expected Deliverables
- code
- tests
- migration if schema changes are required

## Constraints
- keep file handling inside the storage layer
- do not put storage logic into controllers
- preserve current API shape unless a contract update is explicitly required

## Acceptance Criteria
- uploaded file bytes are persisted to storage
- created upload jobs store the source video path
- upload flow still returns clean job summary DTOs
- tests verify file persistence and path assignment

## Notes
If current schema fields are sufficient, prefer using them instead of adding new columns.
