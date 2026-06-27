# Architecture

## Product Type
Self-hosted web application for processing VODs into clip candidates.

## Goal of MVP
The system does not try to create final viral clips automatically.
It helps reduce manual review time by:
- ingesting a VOD
- materializing source media
- transcribing speech
- detecting silence
- analyzing speech activity
- generating candidate clip windows
- supporting manual moderation
- exporting approved clips

> Highlight-pipeline detail (detection vs judgment layers, the LLM's role, arithmetic fusion of orthogonal
> voters, rank-all selection) is owned authoritatively by `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md`.
> The "analysis window scoring / candidate generation" responsibilities below are the system-level summary;
> defer to the brief for how candidates are detected, scored, and ordered.

## High-Level Architecture
- Web UI
- Spring Boot backend
- PostgreSQL database
- Python worker runtime
- Local file storage or MinIO-compatible storage

## Architectural Style
Modular monolith + external async workers

## Core Separation
- `vod_job` is the user-facing aggregate and API-facing status model
- `worker_task` is the operational work unit queued by backend orchestration
- `worker_execution` is the concrete worker lease/attempt record
- `clip_candidate` is the review/export entity
- artifacts should move toward durable storage references instead of ad hoc local path coupling

## Backend Responsibilities
- job creation
- aggregate status tracking
- persistence
- moderation workflow
- task orchestration coordination
- worker callback validation
- APIs for UI and internal worker transport

## Worker Responsibilities
- execute one claimed task at a time
- media download or file consumption
- audio extraction
- transcription
- silence detection
- analysis window scoring
- candidate generation
- clip export execution

## Data Flow
1. User submits URL or file
2. Backend creates `vod_job`
3. Backend enqueues the next `worker_task`
4. Worker claims one task and receives an `executionId`
5. Worker processes media or export work
6. Worker sends progress and final callback for that execution
7. Backend persists outputs and updates aggregate projection
8. UI displays candidates and aggregate state
9. User moderates candidates
10. Backend enqueues export task when needed

## Non-Goals for MVP
- virality prediction
- auto publishing to social platforms
- advanced scene understanding
- multi-user enterprise permissions
- smart clip stitching from distant video segments
- early broker or microservice decomposition without a clear operational need

## Hard Constraints
- Java 21 for backend
- Python 3.11+ for worker
- PostgreSQL as main relational storage
- migration-based DB evolution
- async task processing
- clear JSON contract between backend and worker

## Forbidden Architectural Moves
- microservices split
- business logic in controllers
- direct ffmpeg execution inside controller layer
- tightly coupling backend domain code to Python internals
- collapsing `worker_task` and `worker_execution` back into one flat `job` runtime model
