# TASK-007 Implement Local Storage Abstraction

## Agent
backend-agent

## Summary
Implement a local storage service for predictable media and artifact path management.

## Context
The MVP stores video, audio, and exported clips locally before any optional MinIO support exists.

## Scope
- define storage service interface
- implement local filesystem storage adapter
- resolve stable paths for source video, audio, and exported clips
- document required storage-related configuration

## Out of Scope
- MinIO integration
- cleanup policy
- ffmpeg processing
- upload API

## Inputs
- architecture.md
- data-models.md
- TASK-006.md

## Expected Deliverables
- storage service
- local adapter
- config
- docs

## Constraints
- keep storage local-first
- paths must be deterministic
- avoid leaking filesystem details into unrelated modules

## Acceptance Criteria
- backend can resolve storage paths for a job consistently
- local adapter is configurable by application properties
- implementation is isolated behind a storage abstraction
- storage configuration is documented
