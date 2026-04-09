# StreamCut Backlog

## Purpose
This file is the current source of truth for project progress.
It tracks:
- completed work already implemented
- work currently in progress
- remaining work required to stabilize the MVP and finish the service through `v1.0.0`
- the next architecture track after `v1.0.0`

Last updated: 2026-04-09
Branch snapshot: `develop`
Synced note: Obsidian backlog mirror in `StreamCutProject`

## Sync Policy
- `Documentation/backlog.md` is the source of truth inside the repository.
- the Obsidian backlog mirror in `StreamCutProject` is a synchronized mirror for planning and note-taking.
- Backlog status, ordering, and task inventory should be updated here first and then mirrored into the Obsidian note.

## Current Status
- The MVP now runs as a real full-cycle local service on Docker Compose.
- Backend, PostgreSQL, Liquibase, worker runtime, UI, local storage, and backend <-> worker HTTP transport are wired together.
- The execution model is no longer purely job-status-based:
  - `worker_task` and `worker_execution` exist in source and are persisted in PostgreSQL
  - stale heartbeat recovery is implemented
  - download and processing work are already split operationally
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
- `TASK-042` browser happy-path QA passed in a live browser against the running Docker stack.
- `TASK-043` and `TASK-054` are merged into the validated MVP baseline.
- `TASK-053` moved the validated MVP baseline from `develop` to `main` at commit `22f61b4`.
- `TASK-055`, `TASK-056`, `TASK-057`, and `TASK-058` are merged into `develop`, and `develop` is now the active branch for `v1.0.0` buildout.
- `main` remains the validated MVP baseline while `develop` continues service-hardening.
- The canonical MVP upload policy is defined in `runtime.md`:
  - single-file uploads only
  - supported formats: `video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`, `video/x-msvideo`, `video/mpeg`
  - max file size `512 MB`
  - max request size `520 MB`
  - oversize uploads should surface `413 Payload Too Large`
- The current runtime now enforces the explicit multipart limits from `TASK-040`, and oversize uploads fail with stable `413` semantics.
- Local and production runtime assumptions are now split explicitly:
  - `application-local.yaml` carries local-only defaults
  - `application-prod.yaml` requires explicit runtime inputs
  - `docker-compose.yml` runs the backend under `SPRING_PROFILES_ACTIVE=local`
  - `env.production.example` documents the production env contract without checked-in secrets
- The container/runtime package is now split between:
  - `docker-compose.yml` for the local full stack
  - `docker-compose.production.yml` for the production-oriented package behind the `edge` reverse proxy
  - purpose-fit backend and worker images that no longer inherit from `postgres:15`
  - verified alternative registries for image builds outside Docker Hub-only pull paths
- MinIO is the export artifact store in Docker, and the named volumes `streamcut-postgres`, `streamcut-data`, and `streamcut-minio` are part of the runtime contract.
- Release hardening wave 1 is now complete in source control: upload policy, multipart handling, UI upload guidance, URL/export/restart QA notes, structured runtime logging, integration coverage, Docker runtime notes, release checklist, and status docs have all been updated.
- `TASK-044` was revalidated on the live Docker stack: malformed URL and `404` cases now transition `QUEUED -> FAILED`, persist `JOB_FAILED`, and store readable failure messages instead of leaving jobs stuck in `DOWNLOADING`.
- No task branch is currently ahead of `develop` in this backlog snapshot.
- Worker runtime controls and progress reporting are now implemented in source.
- The worker queue is now split in source into `download-worker` and `processing-worker` roles:
  - jobs enter `QUEUED_FOR_DOWNLOAD`
  - download completion moves them to `QUEUED_FOR_PROCESSING`
  - export remains on the `processing-worker`
- Local and production compose now run one `download-worker` and one `processing-worker` on the same worker image, with future scale expected first on the download side.
- Local Docker validation for the split worker flow was executed on 2026-04-08 against a real uploaded `.mp4`:
  - job created as `QUEUED_FOR_DOWNLOAD`
  - `download-worker` claimed and completed the download/materialization step
  - backend persisted `JOB_DOWNLOAD_COMPLETED` and `JOB_QUEUED_FOR_PROCESSING`
  - `processing-worker` claimed the same job and advanced it through analysis to `READY_FOR_REVIEW`
- The execution model has now moved beyond the original split-worker slice:
  - `worker_task` and `worker_execution` are both active persistence concepts
  - claim flow is task-centric rather than raw `VodJob.status`-centric
  - stale recovery is driven by task heartbeat ownership
  - `VodJob` is increasingly treated as a projection over task state instead of the primary orchestration source
  - task and execution history are exposed in the API, and latest task/execution summaries are visible in the UI
- `TASK-059`, `TASK-060`, `TASK-061`, `TASK-062`, and `TASK-063` are now merged into `develop`.
- `TASK-064` is already on `develop` and currently sits in reviewer/QA validation rather than in the earlier implementation phase:
  - retries, backoff, and dead-letter semantics are still missing
  - a dedicated `TaskTransitionService` does not exist yet
  - export still runs on the `processing-worker`
  - durable-storage-first execution and broker-backed queue semantics are still future work
- `TASK-059` health, readiness, and worker diagnostics are now present in source.
- `TASK-061` operator recovery controls are now present in source.
- `TASK-062` metrics and alertable observability are now present in source.
- `TASK-063` source and artifact retention cleanup is now present in source with follow-up safety fixes merged.
- The repository agent operating system was updated on 2026-04-08 to match the current task-centric model:
  - agent contracts, roles, workflow, and templates now describe `worker_task` and `worker_execution`
  - `Problem Frame` is now a required pre-assignment input for orchestrated agent work
  - legacy task wording was normalized where it was still teaching the old queued-job model

## Task Status Index

### Completed
- `TASK-059` Add Health, Readiness, And Worker Diagnostics
- `TASK-055` Add Authentication And Protected Operator Access
- `TASK-056` Add Security Baseline And Input Hardening
- `TASK-057` Introduce Production Runtime Profiles And Secret Handling
- `TASK-058` Replace MVP Container Strategy And Add Production Edge Runtime
- `TASK-060` Harden Queue Reliability And Stuck-Job Recovery
- `TASK-061` Add Operator Recovery Controls
- `TASK-062` Add Metrics And Alertable Observability
- `TASK-063` Add Source And Artifact Retention Cleanup

### In Progress
- `TASK-064` Add Browser E2E Regression And CI Gate

Status note:
- this backlog pass records a real status transition:
  - `TASK-059` -> completed / merged
  - `TASK-061` -> completed / merged
  - `TASK-062` -> completed / merged
  - `TASK-063` -> completed / merged
  - `TASK-064` -> on `develop`, under reviewer/QA pass
  - `TASK-065` -> next planned task

Current release-track snapshot:
- `TASK-059` through `TASK-063`: merged into `develop`
- `TASK-064`: on `develop`, reviewer/QA in progress
- `TASK-065`: next planned task, operator runbook and recovery documentation

### Planned For `v1.0.0`
- `TASK-064` Add Browser E2E Regression And CI Gate
- `TASK-065` Write Operator Runbook, Backup Restore, And Upgrade Notes
- `TASK-067` Run Backup Restore And Rollback Drill
- `TASK-066` Prepare And Execute `v1.0.0` Release

### Planned Post-`v1.0.0`
- `TASK-068` Expand Task Model With Retry, Backoff, And Dead-Letter Semantics
- `TASK-069` Extract Task Transition Service And Task-Centric Claim Flow
- `TASK-070` Introduce Dedicated `export-worker` Pool
- `TASK-071` Move Artifact Delivery To Signed URLs And Reduce Backend Media Proxying
- `TASK-072` Make Object Storage The Durable Artifact Contract
- `TASK-073` Add Product Quotas And Runtime Limits
- `TASK-074` Prepare Queue Delivery Abstraction For Broker Migration

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
- `TASK-042` Browser QA Pass For Core Happy Path

### Release Hardening
- `TASK-043` Fix Core UI Friction Found During Browser QA
- `TASK-054` Fix Stale Artifact Semantics After Failed Export
- `TASK-055` Add Authentication And Protected Operator Access
- `TASK-056` Add Security Baseline And Input Hardening
- `TASK-057` Introduce Production Runtime Profiles And Secret Handling
- `TASK-058` Replace MVP Container Strategy And Add Production Edge Runtime

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
- `TASK-053` Plan And Execute Release Movement From `develop` To `main`

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
- `TASK-064` is now the active bounded runtime slice.
- Current staged execution:
  - Recovery semantics from `TASK-060` are merged and form the base contract for operator actions
  - Operator recovery controls from `TASK-061` are merged
  - Metrics and alertable observability from `TASK-062` are merged
  - Retention cleanup from `TASK-063` is merged with safety fixes
  - `TASK-064` browser E2E and CI gating is now on `develop` and awaiting final reviewer/QA closure
- Export remains on the `processing-worker` for now.

## NEXT

### Immediate
- `TASK-059` Add Health, Readiness, And Worker Diagnostics
- surface role-specific diagnostics in the UI and health endpoints
- expose task-aware stuck-run diagnostics and operator-facing recovery visibility

### Validation
- The MVP release baseline has been promoted to `main`.

### Stabilization
- `TASK-064` Add Browser E2E Regression And CI Gate
- validate the split worker flow against large URL ingest so download time no longer blocks processing capacity
- close the remaining CI and browser regression gate for release-critical flows
- keep building on the now-merged recovery, observability, and retention slices

### Architectural Follow-Up After `v1.0.0`
- the current `v1.0.0` track hardens the service for a small authenticated operator team
- the next architecture track should build on the now-present task-centric execution model instead of introducing it from scratch
- this follow-up should not preempt the current release gate, but it should already be visible in the source backlog so the project does not drift back into a monolithic `VodJobService` orchestration model

### Release Checklist
- `release-checklist.md` is the current gate document for moving `develop` to `main`.
- The current MVP gate was satisfied and executed through `TASK-053`.
- Future release movement should continue to treat `release-checklist.md` as the gate document.

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
- MVP baseline promoted to `main` through `TASK-053`

### v1.0.0 Buildout
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

### Post-`v1.0.0` Target Architecture Track
- `TASK-068` Expand Task Model With Retry, Backoff, And Dead-Letter Semantics
- `TASK-069` Extract Task Transition Service And Task-Centric Claim Flow
- `TASK-070` Introduce Dedicated `export-worker` Pool
- `TASK-071` Move Artifact Delivery To Signed URLs And Reduce Backend Media Proxying
- `TASK-072` Make Object Storage The Durable Artifact Contract
- `TASK-073` Add Product Quotas And Runtime Limits
- `TASK-074` Prepare Queue Delivery Abstraction For Broker Migration

### Execution Order
1. finish the current MVP release baseline
2. add authentication and security hardening
3. replace local-first runtime compromises with deployable runtime packaging
4. harden recovery, diagnostics, and operator controls
5. add CI, browser regression coverage, runbooks, and a restore drill before release
6. after `v1.0.0`, complete the task-centric architecture slice before introducing a separate queue broker or Kubernetes

### Parallelism Notes
- `TASK-043` has been merged into `develop` and no longer gates the current release baseline.
- `TASK-054` has been merged into `develop` and no longer gates the current release baseline.
- `TASK-055` should complete before `TASK-056`, because the security baseline depends on the chosen auth model.
- `TASK-057` and `TASK-058` can run in parallel once runtime secrets and deployment assumptions are clear.
- `TASK-059` through `TASK-063` are now part of the current `develop` baseline.
- `TASK-059`, `TASK-060`, `TASK-061`, `TASK-062`, and `TASK-063` are now merged, so `TASK-064` and later tasks should treat diagnostics, recovery, observability, and retention behavior as the current baseline rather than as future design work.
- `TASK-064` should start after the main browser flows and negative paths are already stable enough to avoid flaky E2E coverage.
- `TASK-067` should consume the concrete procedures written in `TASK-065`, not invent them during the drill.
- `TASK-066` must not start until every preceding phase has documented evidence.
- `TASK-068` should now expand the already-implemented task model with retry budgets, backoff, and dead-letter handling instead of introducing task persistence from scratch.
- `TASK-069` should extract a dedicated transition/orchestration layer from the current `VodJobService` and projection code now that the task model exists.
- `TASK-070` depends on `TASK-069`, because export routing should sit on explicit task ownership rather than the current mixed processing role.
- `TASK-071` and `TASK-072` can overlap after the durable artifact contract is clear, but `TASK-072` owns the long-term storage contract.
- `TASK-073` should start only after the execution model is explicit enough to enforce concurrency and cost controls coherently.
- `TASK-074` should happen after `TASK-068` and `TASK-069`; broker migration without a clear task contract would just move the current ambiguity into another component.

## LATER

### Product Gaps Still Open
- Error handling and observability are still MVP-level, not hardened operations-grade.
- The current pipeline still stops short of the target architecture described for larger-scale operation:
  - no dedicated `export-worker`
  - no task-level retry budget with dead-letter semantics
  - no explicit `TaskTransitionService`
  - no quotas or fairness controls
  - no durable-storage-first contract for all critical artifacts

### Engineering Follow-Up
- Add a single progress/status document under `agents/` if team workflow needs per-task lifecycle tracking.
- Add decision logging for infrastructure and architecture choices.

## Risks
- The MVP baseline is now aligned on both `main` and `develop`, but the service is still not at the `v1.0.0` operating standard.
- Upload ingest now uses the explicit multipart limits from `TASK-040`, and oversized files fail with stable `413` semantics.
- `TASK-044` has been revalidated for malformed URL and `404` cases; residual coverage gaps remain only for timeout and unsupported-source variants, and they are not release blockers for the current gate.
- Restart resilience on export looks acceptable from `TASK-046`: a worker bounce mid-export recovered and completed instead of ghosting the job.
- `TASK-046` also showed that restart recovery is not very observable: file-backed work and exports can continue, but the API may sit on `DOWNLOADING` or `IN_PROGRESS` without an explicit progress signal.
- Worker cold start depends on external model download and is slower without a configured `HF_TOKEN`.
- Very large URL ingests still create long-running `DOWNLOADING` occupancy; this is the direct reason for moving toward a dedicated `download-worker`.
- The dedicated `download-worker` / `processing-worker` split is now implemented in source, but it still needs live-stack validation and role-specific diagnostics before it can be treated as an operationally closed recovery story.
- Negative-path recovery behavior is partially known from smoke tests, but not yet documented as release-safe behavior.
- `TASK-045` QA found that export retries are allowed, export failure correctly marks the job `FAILED`, and the stale-artifact behavior was addressed in `TASK-054`.
- The current task model is still too thin for scale-out work:
  - no persisted `attempt`
  - no `available_at` / retry backoff control
  - no dead-letter state
  - no task priority or fairness input
- Backend media streaming endpoints still exist for source and export delivery, which is acceptable for the MVP but not the desired long-term contract.

## Recommended Next Sequence
1. `TASK-064` Add Browser E2E Regression And CI Gate.
2. close reviewer and QA pass for `TASK-064`.
3. `TASK-065` Write Operator Runbook, Backup Restore, And Upgrade Notes.
4. `TASK-067` Run Backup Restore And Rollback Drill.
5. `TASK-066` Prepare And Execute `v1.0.0` Release.
6. after `v1.0.0`, continue with `TASK-068` and `TASK-069` on top of the already-implemented `worker_task` / `worker_execution` base before any broker or cluster work.

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
- completed through `TASK-053`

#### Phase 1. Security And Runtime Foundation
- completed: `TASK-055` Add Authentication And Protected Operator Access
- completed: `TASK-056` Add Security Baseline And Input Hardening
- completed: `TASK-057` Introduce Production Runtime Profiles And Secret Handling
- completed: `TASK-058` Replace MVP Container Strategy And Add Production Edge Runtime

#### Phase 2. Reliability And Operator Controls
- completed: `TASK-059` Add Health, Readiness, And Worker Diagnostics
- completed: `TASK-060` Harden Queue Reliability And Stuck-Job Recovery
- completed: `TASK-061` Add Operator Recovery Controls
- completed: `TASK-062` Add Metrics And Alertable Observability
- completed: `TASK-063` Add Source And Artifact Retention Cleanup

#### Phase 3. Quality And Launch
- in progress: `TASK-064` Add Browser E2E Regression And CI Gate
- `TASK-065` Write Operator Runbook, Backup Restore, And Upgrade Notes
- `TASK-067` Run Backup Restore And Rollback Drill
- `TASK-066` Prepare And Execute `v1.0.0` Release

### Post-`v1.0.0` Architecture Phase
1. expand the existing task lifecycle with retries, backoff, and dead-letter behavior
2. separate orchestration from `VodJobService` into a dedicated transition layer
3. split export work into its own pool
4. move media delivery toward signed URLs and durable object storage references
5. add quotas and queue-delivery abstraction only after the execution model is stable
6. migrate operator UI to React when candidate review or multi-developer frontend work makes local component state unavoidable (`TASK-075`)

### Post-`v1.0.0` Architecture Task Queue
- `TASK-068` Expand Task Model With Retry, Backoff, And Dead-Letter Semantics
- `TASK-069` Extract Task Transition Service And Task-Centric Claim Flow
- `TASK-070` Introduce Dedicated `export-worker` Pool
- `TASK-071` Move Artifact Delivery To Signed URLs And Reduce Backend Media Proxying
- `TASK-072` Make Object Storage The Durable Artifact Contract
- `TASK-073` Add Product Quotas And Runtime Limits
- `TASK-074` Prepare Queue Delivery Abstraction For Broker Migration
- `TASK-075` Migrate Operator UI To React

  **Do not start until at least one of the following is true:**
  - Candidate review needs per-candidate local state (scrubber, inline approval, clip preview). The current full-`innerHTML` re-render strategy kills local component state on every 5-second poll; React's reconciliation makes this tractable.
  - A second developer joins frontend work. Template-literal rendering does not scale across contributors.
  - A third interactive panel is needed on the job detail page where optimistic UI or local-only transitions are required.

  **Not a trigger on its own:** file size, "feels like vanilla", or adding a stage progress bar. Those are solvable with targeted DOM patching and a local interval without a framework migration.

  **Scope when the time comes:** Preact is the low-overhead entry point if the backend stays Spring and there is no build infrastructure yet. Full React + Vite is the right call if TypeScript is introduced at the same time.

### Critical Path To v1.0.0
1. Finish the current release baseline and move it intentionally to `main`.
2. Add auth and security hardening before treating the product as a real service.
3. Replace local-first runtime compromises with a deployable runtime package.
4. Close reliability and operator-control gaps so failed jobs are recoverable and diagnosable.
5. Lock the service down with CI, browser E2E, runbooks, and an actual restore drill before the controlled release.
