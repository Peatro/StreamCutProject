# StreamCut Backlog

## Purpose
This file is the current source of truth for project progress.
It tracks:
- completed work already implemented
- work currently in progress
- remaining work required to stabilize the MVP and finish the service through `v1.0.0`

Last updated: 2026-04-06
Branch snapshot: `chore/release-hardening-wave-1`

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
- The canonical MVP upload policy is defined in `runtime.md`:
  - single-file uploads only
  - supported formats: `video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`, `video/x-msvideo`, `video/mpeg`
  - max file size `512 MB`
  - max request size `520 MB`
  - oversize uploads should surface `413 Payload Too Large`
- The current runtime now enforces the explicit multipart limits from `TASK-040`, and oversize uploads fail with stable `413` semantics.
- The Docker image strategy is intentionally MVP-only: backend and worker both inherit from `postgres:15` and layer their own runtimes on top.
- MinIO is the export artifact store in Docker, and the named volumes `streamcut-postgres`, `streamcut-data`, and `streamcut-minio` are part of the runtime contract.
- Release hardening wave 1 is now complete in source control: upload policy, multipart handling, UI upload guidance, URL/export/restart QA notes, structured runtime logging, integration coverage, Docker runtime notes, release checklist, and status docs have all been updated.
- `TASK-044` was revalidated on the live Docker stack after the worker-side fix: a bad `404` URL now transitions `QUEUED -> FAILED`, persists `JOB_FAILED`, and stores a readable download error instead of leaving the job stuck in `DOWNLOADING`.
- The working tree is currently ahead of the last committed release-hardening wave with additional Job page UX/runtime work in progress.

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

### Release Hardening Wave 1
- `TASK-039` Define Upload Size Policy
- `TASK-040` Implement Backend Multipart Limits And Upload Error Handling
- `TASK-041` Expose Upload Constraints In UI
- `TASK-044` Negative Path QA: Invalid URL And Download Failure
- `TASK-045` Negative Path QA: Export Failure And Artifact Failure
- `TASK-046` Negative Path QA: Worker Restart During Queue Processing
- `TASK-048` Add Structured Runtime Logging Around Job Lifecycle
- `TASK-049` Add Focused Integration Tests Around Critical Persistence And API Flows
- `TASK-050` Validate Docker Runtime And Image Hygiene
- `TASK-051` Prepare MVP Release Checklist
- `TASK-052` Document Actual Project Status Against Implemented Task History

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
- `TASK-042` Browser QA Pass For Core Happy Path
- `TASK-043` Fix Core UI Friction Found During Browser QA
- `TASK-054` Fix Stale Artifact Semantics After Failed Export

### Validation
- Browser happy-path QA remains the next release gate.

### Stabilization
- `TASK-053` Plan And Execute Release Movement From `develop` To `main`

### Release Checklist
- `release-checklist.md` is the current gate document for moving `develop` to `main`.
- Release movement is a hard `no-go` until the checklist evidence is complete.
- `TASK-053` should not execute until `release-checklist.md` is satisfied.

## ROADMAP TO FULL SERVICE

### Phase A. Full-Cycle MVP Loop
Status: completed locally on 2026-04-06
- uploaded source files persist to shared storage
- jobs auto-queue on creation
- backend and worker communicate through HTTP polling/callback
- worker executes analysis and export tasks
- backend persists processing results and serves exported artifacts

### Phase B. Release Hardening
- Define and implement realistic upload size handling from the policy in `runtime.md`.
- Verify UI flows in a real browser against the live stack.
- Verify failure and recovery paths on the live stack.
- Improve failure visibility and log traceability.
- Prepare release checklist and merge path to `main`.

## PLANNED TASK QUEUE

### Current Release Baseline
- `TASK-042` Browser QA Pass For Core Happy Path
- `TASK-043` Fix Core UI Friction Found During Browser QA
- `TASK-054` Fix Stale Artifact Semantics After Failed Export
- `TASK-053` Plan And Execute Release Movement From `develop` To `main`

### v1.0.0 Buildout
- `TASK-055` Add Authentication And Protected Operator Access
- `TASK-056` Add Security Baseline And Input Hardening
- `TASK-057` Introduce Production Runtime Profiles And Secret Handling
- `TASK-058` Replace MVP Container Strategy And Add Production Edge Runtime
- `TASK-059` Add Health, Readiness, And Worker Diagnostics
- `TASK-060` Harden Queue Reliability And Stuck-Job Recovery
- `TASK-061` Add Operator Recovery Controls
- `TASK-062` Add Metrics And Alertable Observability
- `TASK-063` Add Source And Artifact Retention Cleanup
- `TASK-064` Add Browser E2E Regression And CI Gate
- `TASK-065` Write Operator Runbook, Backup Restore, And Upgrade Notes
- `TASK-067` Run Backup Restore And Rollback Drill
- `TASK-066` Prepare And Execute `v1.0.0` Release

### Execution Order
1. finish the current MVP release baseline
2. add authentication and security hardening
3. replace local-first runtime compromises with deployable runtime packaging
4. harden recovery, diagnostics, and operator controls
5. add CI, browser regression coverage, runbooks, and a restore drill before release

### Parallelism Notes
- `TASK-043` should consume only concrete findings from `TASK-042`.
- `TASK-054` should close before `TASK-053` and before any production-facing release claim.
- `TASK-055` should complete before `TASK-056`, because the security baseline depends on the chosen auth model.
- `TASK-057` and `TASK-058` can run in parallel once runtime secrets and deployment assumptions are clear.
- `TASK-059` through `TASK-063` can overlap, but `TASK-060` owns recovery semantics and should define the contract for `TASK-061` and parts of `TASK-062`.
- `TASK-064` should start after the main browser flows and negative paths are already stable enough to avoid flaky E2E coverage.
- `TASK-067` should consume the concrete procedures written in `TASK-065`, not invent them during the drill.
- `TASK-066` must not start until every preceding phase has documented evidence.

## LATER

### Product Gaps Still Open
- Error handling and observability are still MVP-level, not hardened operations-grade.

### Engineering Follow-Up
- Add a single progress/status document under `agents/` if team workflow needs per-task lifecycle tracking.
- Add decision logging for infrastructure and architecture choices.

## Risks
- The codebase and backlog are now aligned better, but `main` still lags behind current delivery.
- `main` does not yet represent the current MVP state.
- Upload ingest now uses the explicit multipart limits from `TASK-040`, and oversized files fail with stable `413` semantics.
- Docker image choices are acceptable for the current MVP but remain a deliberate compromise rather than a production recommendation.
- Release readiness is still blocked by incomplete browser QA and the remaining stabilization gates.
- Restart resilience on export looks acceptable from `TASK-046`: a worker bounce mid-export recovered and completed instead of ghosting the job.
- `TASK-046` also showed that restart recovery is not very observable: file-backed work and exports can continue, but the API may sit on `DOWNLOADING` or `IN_PROGRESS` without an explicit progress signal.
- Worker cold start depends on external model download and is slower without a configured `HF_TOKEN`.
- Browser happy-path QA has not yet been completed as a formal release gate.
- Negative-path recovery behavior is partially known from smoke tests, but not yet documented as release-safe behavior.
- `TASK-045` QA found that export retries are allowed, export failure correctly marks the job `FAILED`, but stale artifact endpoints can still return `200` during a failed retry because an old object remains accessible. This is now tracked as `TASK-054`.

## Recommended Next Sequence
1. `TASK-042` Browser QA Pass For Core Happy Path.
2. `TASK-043` Fix Core UI Friction Found During Browser QA.
3. `TASK-054` Fix Stale Artifact Semantics After Failed Export.
4. `TASK-053` Plan And Execute Release Movement From `develop` To `main`.

## Path To Service v1.0.0

### v1.0.0 Assumptions
- `v1.0.0` means a finished operator-facing service for a small authenticated team, not a public consumer app.
- The supported service shape is single-tenant: one backend, one or more workers, PostgreSQL, and S3-compatible object storage.
- `v1.0.0` must be deployable outside local Docker, with documented secrets, recovery procedures, and operator diagnostics.
- `v1.0.0` does not include billing, multi-tenant workspaces, model training loops, or candidate-ranking research.

### v1.0.0 Definition Of Done
- `main` represents the validated service release rather than a stale MVP branch.
- The service is authenticated and no longer exposes the operator UI and write APIs without access control.
- Runtime configuration, container images, and edge/proxy setup are production-credible for a small deployment.
- Operators can diagnose, retry, cancel, and recover jobs without database surgery.
- Metrics, health/readiness, cleanup policy, and backup/restore guidance exist.
- Backup, restore, and rollback behavior have been exercised at least once against the chosen runtime rather than only described on paper.
- Browser E2E and CI gates protect the main operator flows.

### v1.0.0 Phases
1. Release the current validated MVP baseline.
2. Add security and runtime foundations for a real service.
3. Harden reliability, recovery, and observability for operators.
4. Add launch-quality automation, runbooks, and final release discipline.

### v1.0.0 Task Queue

#### Phase 0. Release Baseline
- `TASK-042` Browser QA Pass For Core Happy Path
- `TASK-043` Fix Core UI Friction Found During Browser QA
- `TASK-054` Fix Stale Artifact Semantics After Failed Export
- `TASK-053` Plan And Execute Release Movement From `develop` To `main`

#### Phase 1. Security And Runtime Foundation
- `TASK-055` Add Authentication And Protected Operator Access
- `TASK-056` Add Security Baseline And Input Hardening
- `TASK-057` Introduce Production Runtime Profiles And Secret Handling
- `TASK-058` Replace MVP Container Strategy And Add Production Edge Runtime

#### Phase 2. Reliability And Operator Controls
- `TASK-059` Add Health, Readiness, And Worker Diagnostics
- `TASK-060` Harden Queue Reliability And Stuck-Job Recovery
- `TASK-061` Add Operator Recovery Controls
- `TASK-062` Add Metrics And Alertable Observability
- `TASK-063` Add Source And Artifact Retention Cleanup

#### Phase 3. Quality And Launch
- `TASK-064` Add Browser E2E Regression And CI Gate
- `TASK-065` Write Operator Runbook, Backup Restore, And Upgrade Notes
- `TASK-067` Run Backup Restore And Rollback Drill
- `TASK-066` Prepare And Execute `v1.0.0` Release

### Critical Path To v1.0.0
1. Finish the current release baseline and move it intentionally to `main`.
2. Add auth and security hardening before treating the product as a real service.
3. Replace local-first runtime compromises with a deployable runtime package.
4. Close reliability and operator-control gaps so failed jobs are recoverable and diagnosable.
5. Lock the service down with CI, browser E2E, runbooks, and an actual restore drill before the controlled release.
