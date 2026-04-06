# StreamCut Backlog

## Purpose
This file is the current source of truth for project progress.
It tracks:
- completed work already implemented
- work currently in progress
- remaining work required to stabilize and ship the MVP

Last updated: 2026-04-06
Branch snapshot: `develop`

## Current Status
- The MVP now runs as a real full-cycle local service on Docker Compose.
- Backend, PostgreSQL, Liquibase, worker runtime, UI, local storage, and backend <-> worker HTTP transport are wired together.
- URL ingest was validated end-to-end on 2026-04-06:
  - create job
  - auto-queue
  - worker claim
  - analysis completion
  - candidate approval
  - export completion
  - artifact download
- Upload ingest was validated on 2026-04-06 through create -> queue -> worker claim -> `READY_FOR_REVIEW`.
- Clean-slate Docker validation was executed on 2026-04-06 after volume reset:
  - URL ingest completed end-to-end
  - upload ingest completed end-to-end on a valid small file
  - export artifact download and stream endpoints returned `200`
- `main` is still behind the current delivery state.
- One runtime limitation is now confirmed locally:
  - upload requests above the default multipart limit currently return `413 Maximum upload size exceeded`

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
- `TASK-036` Add Artifact Access And UI Runtime Controls
- `TASK-038` Run QA Release Validation

### Backend <-> Worker Full-Cycle Loop
- `TASK-026` Freeze Worker Transport Contract
- `TASK-027` Persist Uploaded Source Files
- `TASK-028` Auto Queue Jobs On Creation
- `TASK-029` Implement Worker Claim API
- `TASK-030` Implement Worker Result Ingestion API
- `TASK-031` Implement Worker Failure Reporting API
- `TASK-035` Implement Export Dispatch And Completion
- `TASK-037` Add End-To-End Integration Coverage

### Worker Runtime Completion
- `TASK-032` Implement Worker Polling Loop
- `TASK-033` Implement Worker Job Runner
- `TASK-034` Complete Candidate Generation Pipeline

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
- Full backend <-> worker local Docker validation executed on 2026-04-06:
  - `GET /health`
  - `POST /api/jobs/url`
  - `GET /api/jobs/{id}`
  - `GET /api/jobs/{id}/events`
  - `GET /api/jobs/{id}/candidates`
  - `POST /api/candidates/{id}/approve`
  - `POST /api/candidates/{id}/export`
  - `GET /api/exports/{id}`
  - `GET /api/exports/{id}/file`
  - `POST /api/jobs/upload`
- Confirmed runtime outcomes from that validation:
  - URL ingest reached `COMPLETED`
  - export artifact was downloadable from backend
  - upload ingest reached `READY_FOR_REVIEW`
  - larger upload sample hit `413 Maximum upload size exceeded`
- Clean-slate release-hardening validation on Docker volumes reset confirmed:
  - URL ingest reached `COMPLETED`
  - file upload ingest reached `COMPLETED` on a small valid media sample
  - export artifacts were stored in MinIO-backed object storage
  - `/api/exports/{id}/file` and `/api/exports/{id}/stream` served artifacts correctly

## IN_PROGRESS
- No active implementation task is currently open in this backlog snapshot.
- Next work should be chosen from release hardening, stabilization, and release preparation.

## NEXT

### Immediate
- `TASK-039` Define Upload Size Policy
- `TASK-040` Implement Backend Multipart Limits And Upload Error Handling
- `TASK-041` Expose Upload Constraints In UI

### Validation
- `TASK-042` Browser QA Pass For Core Happy Path
- `TASK-044` Negative Path QA: Invalid URL And Download Failure
- `TASK-045` Negative Path QA: Export Failure And Artifact Failure
- `TASK-046` Negative Path QA: Worker Restart During Queue Processing

### Stabilization
- `TASK-047` Improve Failure State Visibility In Backend And UI
- `TASK-048` Add Structured Runtime Logging Around Job Lifecycle
- `TASK-049` Add Focused Integration Tests Around Critical Persistence And API Flows
- `TASK-050` Validate Docker Runtime And Image Hygiene
- `TASK-051` Prepare MVP Release Checklist
- `TASK-052` Document Actual Project Status Against Implemented Task History
- `TASK-053` Plan And Execute Release Movement From `develop` To `main`

## ROADMAP TO FULL SERVICE

### Phase A. Full-Cycle MVP Loop
Status: completed locally on 2026-04-06
- uploaded source files persist to shared storage
- jobs auto-queue on creation
- backend and worker communicate through HTTP polling/callback
- worker executes analysis and export tasks
- backend persists processing results and serves exported artifacts

### Phase B. Release Hardening
- Define and implement realistic upload size handling.
- Verify UI flows in a real browser against the live stack.
- Verify failure and recovery paths on the live stack.
- Improve failure visibility and log traceability.
- Prepare release checklist and merge path to `main`.

## PLANNED TASK QUEUE

### Ready For Execution
- `TASK-039` Define Upload Size Policy
- `TASK-040` Implement Backend Multipart Limits And Upload Error Handling
- `TASK-041` Expose Upload Constraints In UI
- `TASK-042` Browser QA Pass For Core Happy Path
- `TASK-043` Fix Core UI Friction Found During Browser QA
- `TASK-044` Negative Path QA: Invalid URL And Download Failure
- `TASK-045` Negative Path QA: Export Failure And Artifact Failure
- `TASK-046` Negative Path QA: Worker Restart During Queue Processing
- `TASK-047` Improve Failure State Visibility In Backend And UI
- `TASK-048` Add Structured Runtime Logging Around Job Lifecycle
- `TASK-049` Add Focused Integration Tests Around Critical Persistence And API Flows
- `TASK-050` Validate Docker Runtime And Image Hygiene
- `TASK-051` Prepare MVP Release Checklist
- `TASK-052` Document Actual Project Status Against Implemented Task History
- `TASK-053` Plan And Execute Release Movement From `develop` To `main`

### Execution Order
- Release-hardening order should now proceed as:
1. upload size policy
2. backend upload handling
3. browser UI verification and UX fixes
4. negative-path QA
5. observability and failure clarity
6. release prep and `main` merge plan

### Parallelism Notes
- `TASK-039` should complete before `TASK-040` and `TASK-041`.
- `TASK-042` and `TASK-044` can run in parallel after upload constraints are clarified.
- `TASK-043` should consume only concrete findings from `TASK-042`.
- `TASK-051` and `TASK-052` can run in parallel once stabilization work is mostly complete.
- `TASK-053` must not start until QA and release checklist gates are satisfied.

## LATER

### Product Gaps Still Open
- Error handling and observability are still MVP-level, not hardened operations-grade.

### Engineering Follow-Up
- Add a single progress/status document under `agents/` if team workflow needs per-task lifecycle tracking.
- Add decision logging for infrastructure and architecture choices.

## Risks
- The codebase and backlog are now aligned better, but `main` still lags behind current delivery.
- `main` does not yet represent the current MVP state.
- Upload ingest is not release-ready for realistic file sizes until multipart limits are configured explicitly.
- Worker cold start depends on external model download and is slower without a configured `HF_TOKEN`.
- Browser happy-path QA has not yet been completed as a formal release gate.
- Negative-path recovery behavior is partially known from smoke tests, but not yet documented as release-safe behavior.

## Recommended Next Sequence
1. `TASK-039` Define Upload Size Policy.
2. `TASK-040` Implement Backend Multipart Limits And Upload Error Handling.
3. `TASK-041` Expose Upload Constraints In UI.
4. `TASK-042` Browser QA Pass For Core Happy Path.
5. `TASK-043` Fix Core UI Friction Found During Browser QA.
6. `TASK-044` through `TASK-046` negative-path QA.
7. `TASK-047` through `TASK-050` stabilization and runtime clarity.
8. `TASK-051` through `TASK-053` release preparation and movement to `main`.
