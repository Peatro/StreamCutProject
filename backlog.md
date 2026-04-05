# StreamCut Backlog

## Purpose
This file is the current source of truth for project progress.
It tracks:
- completed work already implemented
- work currently in progress
- remaining work required to stabilize and ship the MVP

Last updated: 2026-04-05
Branch snapshot: `develop`

## Current Status
- Core MVP skeleton is implemented in code.
- Backend, PostgreSQL, Liquibase, worker skeleton, UI, and local compose are present.
- `feature/TASK-004-persistence-foundation` was merged into `develop`.
- `main` is behind `develop` and does not contain the current MVP implementation yet.
- Docker runtime has been repaired locally and is working on `develop`.
- Smoke-test on the current Docker stack has been completed without confirmed code defects.
- The service is still not full-cycle operational because job creation does not yet drive a real backend -> worker -> backend processing loop.

## DONE

### Docs And Process
- `init`
- Git workflow defined
- Agent operating model created
- backlog of implementation tasks created

### Backend Foundation
- `TASK-001` Bootstrap Backend
- `TASK-004` Configure Persistence Foundation
- `TASK-010` Create Job Entity
- `TASK-011` Create Job API
- `TASK-005` Create Job Details API
- `TASK-006` Create Job Upload API
- `TASK-007` Implement Local Storage Abstraction
- `TASK-008` Add Job Event Persistence And API
- `TASK-009` Implement Worker Dispatch Contract

### Backend Processing Data
- `TASK-012` Create Transcript Persistence
- `TASK-013` Add Transcript API
- `TASK-016` Create Silence Segment Persistence
- `TASK-018` Create Analysis Window Persistence
- `TASK-020` Create Candidate Persistence And Moderation API
- `TASK-023` Implement Export API And Status Tracking

### Worker
- `TASK-002` Bootstrap Python Worker
- `TASK-014` Implement Worker Audio Extraction
- `TASK-015` Implement Worker Transcription
- `TASK-017` Implement Worker Silence Detection
- `TASK-019` Implement Worker Candidate Analysis
- `TASK-024` Implement Worker Clip Export

### UI And Runtime
- `TASK-021` Build Job List And Details UI
- `TASK-022` Build Candidate Review UI
- `TASK-003` Docker Compose Setup
- `TASK-025` Harden MVP Runtime

### Important Corrections Already Applied
- Removed accidental Java-side worker audio implementation to preserve architecture boundaries.
- Merged MVP feature branch into `develop`.
- Full API smoke-test executed on the live Docker stack:
  - `GET /health`
  - `GET /`
  - `GET /job.html?id=1`
  - `POST /api/jobs/url`
  - `POST /api/jobs/upload`
  - `GET /api/jobs`
  - `GET /api/jobs/{id}`
  - `GET /api/jobs/{id}/transcript`
  - `GET /api/jobs/{id}/candidates`
  - `GET /api/jobs/{id}/events`
  - `POST /api/candidates/{id}/approve`
  - `POST /api/candidates/{id}/export`
  - `GET /api/exports/{id}`
- Smoke-test data was cleaned from the local PostgreSQL volume after validation.

## IN_PROGRESS

### Docker Runtime Fix
Status: implemented and validated locally, pending follow-up cleanup decisions

Scope completed locally:
- backend Docker image now builds from local `bootJar`
- Liquibase startup works in Docker
- worker container stays alive instead of exiting immediately
- compose stack starts successfully
- smoke checks passed for:
  - `GET /health`
  - `POST /api/jobs/url`
  - `GET /api/jobs`
  - PostgreSQL table creation through Liquibase

Files currently changed:
- `.dockerignore`
- `Dockerfile.backend`
- `build.gradle.kts`
- `docker-compose.yml`
- `worker/Dockerfile`
- `worker/src/streamcut_worker/main.py`

## NEXT

### Immediate
- Review whether `postgres:15` in compose is intentional long-term or only a local compatibility workaround.
- Decide whether backend Docker image should stay jar-based or be rebuilt as a cleaner multi-stage image later.
- Decide whether worker should remain long-running idle by default or move to explicit queue/command mode.

### Validation
- Manually verify UI flows in a real browser.
- Run worker-side functional checks with an actual input media file.
- Run a true end-to-end job execution once backend-to-worker processing is wired beyond the current lightweight runtime loop.

### Stabilization
- Document current actual delivery status against `TASK-001` ... `TASK-025`.
- Decide release path from `develop` to `main`.
- Add more integration tests around persistence and API flows.

## ROADMAP TO FULL SERVICE

### Phase A. Close The Ingest Gap
- Persist uploaded source files to storage instead of storing only filenames.
- Store actual source and audio paths on `vod_job`.
- Queue jobs automatically at creation time.
- Record explicit job events for create, queue, start, finish, and failure.

### Phase B. Wire Real Backend <-> Worker Transport
- Freeze the worker transport strategy for MVP as HTTP polling/callback, not a broker.
- Add a backend API for worker job claim/poll.
- Add backend APIs for worker result submission and worker failure reporting.
- Keep the payloads aligned with `worker-protocol.md`.

### Phase C. Make Worker Execute Real Jobs
- Implement a real worker polling loop.
- Build a job runner that executes the current pipeline services in sequence.
- Support both URL ingest and uploaded-file ingest.
- Report state transitions and failures back to backend.

### Phase D. Persist Processing Results End-To-End
- Ingest transcript, silence segments, analysis windows, and clip candidates from worker output.
- Move jobs to `READY_FOR_REVIEW` once candidate generation is complete.
- Ensure transcript/candidate/event UI surfaces are driven by real processing output instead of test data.

### Phase E. Complete Export Loop
- Dispatch export work through the same backend -> worker mechanism.
- Have worker perform ffmpeg clip export against approved candidates.
- Report export completion/failure back to backend.
- Expose download access to exported artifacts in UI/API.

### Phase F. Validate And Release
- Run a real media-file end-to-end flow.
- Run URL ingest end-to-end.
- Verify obvious failure paths and recovery.
- Merge to `main` only after these checks pass cleanly.

## PLANNED TASK QUEUE

### Ready For Execution
- `TASK-026` Freeze Worker Transport Contract
- `TASK-027` Persist Uploaded Source Files
- `TASK-028` Auto Queue Jobs On Creation
- `TASK-029` Implement Worker Claim API
- `TASK-030` Implement Worker Result Ingestion API
- `TASK-031` Implement Worker Failure Reporting API
- `TASK-032` Implement Worker Polling Loop
- `TASK-033` Implement Worker Job Runner
- `TASK-034` Complete Candidate Generation Pipeline
- `TASK-035` Implement Export Dispatch And Completion
- `TASK-036` Add Artifact Access And UI Runtime Controls
- `TASK-037` Add End-To-End Integration Coverage
- `TASK-038` Run QA Release Validation

### Execution Order
1. `TASK-026`
2. `TASK-027`
3. `TASK-028`
4. `TASK-029`
5. `TASK-030`
6. `TASK-031`
7. `TASK-032`
8. `TASK-033`
9. `TASK-034`
10. `TASK-035`
11. `TASK-036`
12. `TASK-037`
13. `TASK-038`

### Parallelism Notes
- `TASK-027` and `TASK-026` can proceed independently.
- `TASK-029`, `TASK-030`, and `TASK-031` are separate backend slices but should share the transport decision from `TASK-026`.
- `TASK-032` can start once `TASK-029` is stable.
- `TASK-033` depends on `TASK-032` and the existing worker service modules.
- `TASK-036` should wait until `TASK-035` exposes real artifact state.
- `TASK-038` is a gate, not a feature task.

## LATER

### Product Gaps Still Open
- Error handling and observability are still MVP-level, not hardened operations-grade.

### Engineering Follow-Up
- Add a single progress/status document under `agents/` if team workflow needs per-task lifecycle tracking.
- Add decision logging for infrastructure and architecture choices.

## Risks
- The codebase is ahead of the documentation of actual delivery status.
- `main` does not yet represent the current MVP state.
- The worker is structurally present, but real media pipeline validation is still thinner than backend validation.

## Recommended Next Sequence
1. Decide the permanent Docker baseline for backend, worker, and postgres images.
2. Manually verify the UI in a real browser.
3. Run a real media-file end-to-end flow through the worker pipeline.
4. Update `main` only after `develop` passes the remaining validation cleanly.
