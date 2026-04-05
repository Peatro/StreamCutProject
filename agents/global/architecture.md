# Architecture

## Product Type
Self-hosted web application for processing VODs into clip candidates.

## Goal of MVP
The system does not try to create final viral clips automatically.
It helps reduce manual review time by:
- ingesting a VOD
- transcribing speech
- detecting silence
- analyzing speech activity
- generating candidate clip windows
- supporting manual moderation
- exporting approved clips

## High-Level Architecture
- Web UI
- Spring Boot backend
- PostgreSQL database
- Python worker
- Local file storage or MinIO-compatible storage

## Architectural Style
Modular monolith + external async worker

## Backend Responsibilities
- job creation
- status tracking
- persistence
- moderation workflow
- export orchestration
- APIs for UI

## Worker Responsibilities
- media download or file consumption
- audio extraction
- transcription
- silence detection
- analysis window scoring
- candidate generation
- clip export execution

## Data Flow
1. User submits URL or file
2. Backend creates job
3. Backend dispatches work
4. Worker processes media
5. Worker returns structured results
6. Backend persists results
7. UI displays candidates
8. User moderates candidates
9. Backend requests export
10. Worker exports clip

## Non-Goals for MVP
- virality prediction
- auto publishing to social platforms
- advanced scene understanding
- multi-user enterprise permissions
- smart clip stitching from distant video segments

## Hard Constraints
- Java 21 for backend
- Python 3.11+ for worker
- PostgreSQL as main relational storage
- Migration-based DB evolution
- Async job processing
- Clear JSON contract between backend and worker

## Forbidden Architectural Moves
- microservices split
- business logic in controllers
- direct ffmpeg execution inside controller layer
- tightly coupling backend domain code to Python internals
