# StreamCut Backlog

## Purpose
This file is the current source of truth for project progress.
It tracks:
- completed work already implemented
- work currently in progress
- remaining work required to stabilize the MVP and finish the service through `v1.0.0`
- the next architecture track after `v1.0.0`

Last updated: 2026-06-18
Branch snapshot: `develop` carries post-`v1.0.0` architecture work through `TASK-072`, plus the product-quality track (`TASK-076` through `TASK-081`) and worker tuning fixes ahead of `main`
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
- `TASK-055` through `TASK-065` were merged into `develop` during the `v1.0.0` service-hardening track.
- `TASK-067` rerun passed on 2026-04-09 with backup, restore, export recovery, and post-restore health evidence captured under `C:\Users\Peatr\AppData\Local\Temp\streamcut-task067-rerun`.
- The release-candidate defect found during the drill was fixed before release: processing workers now defer queued `ANALYZE` claims until the referenced source video exists under `APP_STORAGE_LOCAL_ROOT`.
- `TASK-066` cut the controlled `v1.0.0` release through `release/1.0.0`, merged it to `main`, and back-merged it to `develop`.
- `main` contains the `v1.0.0` release state identified by git tag `v1.0.0`, and `develop` now carries that baseline plus additional post-release fixes.
- Release notes and known limitations for this cut are recorded in `Documentation/v1.0.0-release.md`.
- The repository now has a top-level `README.md` that serves as the landing page for overview, quick start, and documentation entry points.
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
  - `compose.streamcut.yml` for the production-oriented package behind the `edge` reverse proxy
  - purpose-fit backend and worker images that no longer inherit from `postgres:15`
  - verified alternative registries for image builds outside Docker Hub-only pull paths
- MinIO is the export artifact store in Docker, and the named volumes `streamcut-postgres`, `streamcut-data`, and `streamcut-minio` are part of the runtime contract.
- Release hardening wave 1 is now complete in source control: upload policy, multipart handling, UI upload guidance, URL/export/restart QA notes, structured runtime logging, integration coverage, Docker runtime notes, release checklist, and status docs have all been updated.
- `TASK-044` was revalidated on the live Docker stack: malformed URL and `404` cases now transition `QUEUED -> FAILED`, persist `JOB_FAILED`, and store readable failure messages instead of leaving jobs stuck in `DOWNLOADING`.
- No task branch is currently ahead of `develop` in this backlog snapshot.
- Worker runtime controls and progress reporting are now implemented in source.
- The worker queue is now split in source into `download-worker`, `processing-worker`, and `export-worker` roles:
  - jobs enter `QUEUED_FOR_DOWNLOAD`
  - download completion moves them to `QUEUED_FOR_PROCESSING`
  - approved export tasks are claimed only by `export-worker`
- Local and production compose now run one `download-worker`, one `processing-worker`, and one `export-worker` on the same worker image, with GPU override still scoped only to `processing-worker`.
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
- Processing workers can now report the whisper device they claimed with:
  - `worker_execution.whisper_device` persisted in PostgreSQL
  - latest execution summaries and execution history exposing the reported device in the API/UI
  - an optional `docker-compose.gpu.yml` local override keeping the CPU-first runtime as the default path
- Download worker failures now surface platform-downloader stderr more clearly, and platform downloads are constrained to the intended primary result instead of ambiguous multi-item fetches.
- Candidate generation is no longer hard-limited to 10 review items:
  - worker-side analysis now returns all non-overlapping candidates
  - zero-candidate completion is surfaced explicitly instead of pretending candidates are ready
- Worker progress reporting is now throttled and trimmed:
  - workers emit stage-local progress updates at a bounded rate instead of flooding the backend
  - transient `WORKER_PROGRESS` event rows are cleaned during stage transitions and completion to avoid avoidable database growth
- Candidate review pagination is now present in the job detail UI so large result sets do not exhaust browser memory.
- `TASK-059`, `TASK-060`, `TASK-061`, `TASK-062`, and `TASK-063` are now merged into `develop`.
- `TASK-064` browser E2E coverage and CI gating are now present in source:
  - the repository contains a dedicated `src/e2eTest` suite
  - `.github/workflows/ci.yml` runs `./gradlew e2eTest` after backend tests
  - local execution is documented in `src/e2eTest/README.md`
- `TASK-065` operator runbook, backup/restore, and upgrade guidance are now present in source through `Documentation/runbook.md`.
- Signed export delivery and the durable object-storage contract are now present in source:
  - completed exports prefer temporary signed object-storage URLs over backend proxying when S3 mode is enabled
  - durable source and export references are persisted as the long-term artifact contract
- The product-quality track (`TASK-076` through `TASK-081`) is now merged into `develop`:
  - clip candidate scoring now incorporates an audio loudness signal alongside transcript density and silence (`TASK-076`, `4e722b0`)
  - download heartbeat is gated on real byte progress so stalled downloads auto-recover via the existing stale timeout (`TASK-077`, `987d710`)
  - clip export uses a single muxed Twitch format (fixing ~12s audio desync) and two-stage seek with bounded `-threads` (fixing CPU hog) (`TASK-080`, `76760e8`)
  - jobs auto-complete when all candidates are moderated and all approved exports finish; manual `POST /api/jobs/{id}/complete` is available; delete is now allowed from `READY_FOR_REVIEW` (`TASK-079`, `0dd3992`)
  - job list UI adds delete button, bulk clear, manual complete button, and a static progress bar at rest (`TASK-078`, `b5eb976`)
  - yt-dlp concurrent fragment downloads raised from 4 to 8 (`5eaa0bd`)
  - Twitch VOD downloads now use TwitchDownloaderCLI instead of yt-dlp to fix A/V desync; other platform URLs still use yt-dlp (`TASK-081`, merged into develop)
- The remaining larger architecture gaps are still future work:
  - quotas and fairness controls are still future work (deprioritized: not justified for single-operator/self use; revisit when going public)
  - broker-backed queue semantics are still future work (deprioritized: DB polling is fine at n=1; evidence-gated)
  - React UI migration is still future work (deprioritized: current vanilla JS UI is sufficient for operator self-use)
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
- `TASK-066` Prepare And Execute `v1.0.0` Release
- `TASK-067` Run Backup Restore And Rollback Drill
- `TASK-068` Expand Task Model With Retry, Backoff, And Dead-Letter Semantics
- `TASK-069` Extract Task Transition Service And Task-Centric Claim Flow
- `TASK-070` Introduce Dedicated `export-worker` Pool
- `TASK-071` Move Artifact Delivery To Signed URLs And Reduce Backend Media Proxying
- `TASK-072` Make Object Storage The Durable Artifact Contract
- `TASK-076` Add Audio Loudness Signal And Re-Weight Clip Candidate Scoring (`4e722b0`)
- `TASK-077` Make Download Stall Detectable By Gating Heartbeat On Real Progress (`987d710`)
- `TASK-078` Job List/Detail UI: Delete, Bulk Clear, Complete, Static Progress Bar (`b5eb976`)
- `TASK-079` Job Lifecycle On Moderation: Auto-Complete, Manual Complete, Delete From Review (`0dd3992`)
- `TASK-080` Fix Clip Export: Single Muxed Format + Two-Stage Seek + Bounded Threads (`76760e8`)
- `TASK-081` Use TwitchDownloaderCLI For Twitch Sources To Fix A/V Desync (merged into develop)
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

### In Progress
- none currently

Status note:
- this backlog pass records a real status transition:
  - `TASK-081` -> completed / merged into develop (pending commit)
- prior status transitions recorded:
  - `TASK-076` -> completed / merged (`4e722b0`)
  - `TASK-077` -> completed / merged (`987d710`)
  - `TASK-078` -> completed / merged (`b5eb976`)
  - `TASK-079` -> completed / merged (`0dd3992`)
  - `TASK-080` -> completed / merged (`76760e8`)
  - `TASK-068` through `TASK-072` -> completed / merged (post-`v1.0.0` architecture track)
  - `TASK-064`, `TASK-065` -> completed / merged
  - `TASK-067` -> completed / passed on 2026-04-09
  - `TASK-066` -> completed / `v1.0.0` released

Current release-track snapshot:
- `v1.0.0` is released on `main` and back-merged into `develop`
- the release point is explicit and reproducible through git tag `v1.0.0`
- `develop` now carries the checked-in post-release architecture baseline through `TASK-072` plus the product-quality track through `TASK-081`
- the product-quality track was driven by real operator-use feedback on Twitch VOD clip selection
- the next tracked work starts at `TASK-073` (deprioritized) or the next operator-use issue

### Closed In `v1.0.0`
- `TASK-067` Run Backup Restore And Rollback Drill
- `TASK-066` Prepare And Execute `v1.0.0` Release

### Completed (Product Quality Track)
- `TASK-076` Add Audio Loudness Signal And Re-Weight Clip Candidate Scoring (`4e722b0`)
  - direction shift: primary goal is a fully working clip-selection tool for the operator's own Twitch VODs; clip-selection quality leads, multi-tenant work follows
  - added ffmpeg loudness signal so laughter/hype outranks monologue in clip scoring
- `TASK-077` Make Download Stall Detectable By Gating Heartbeat On Real Progress (`987d710`)
  - heartbeat now only refreshes when downloaded_bytes actually advance, so the existing stale timeout + recovery fires on frozen downloads
- `TASK-079` Job Lifecycle On Moderation (`0dd3992`)
  - auto-complete when all candidates moderated and all approved exports finished
  - manual `POST /api/jobs/{id}/complete` (409 if not `READY_FOR_REVIEW`)
  - `DELETE /api/jobs/{id}` now allowed from `READY_FOR_REVIEW`
- `TASK-078` Job List/Detail UI (`b5eb976`)
  - delete button, bulk clear, manual complete button, static progress bar at rest
- `TASK-080` Fix Clip Export (`76760e8`)
  - audio desync fixed: single muxed Twitch format instead of merged separate audio HLS (~12s drift eliminated)
  - CPU hog fixed: two-stage seek + bounded `-threads` (export 16s vs minutes at 945% CPU)
- `TASK-081` Use TwitchDownloaderCLI For Twitch Sources To Fix A/V Desync (merged into develop)
  - Twitch VOD downloads now route to TwitchDownloaderCLI v1.56.4 instead of yt-dlp, eliminating residual A/V desync on Twitch sources
  - non-Twitch platform URLs continue to use yt-dlp unchanged

### Planned Post-`v1.0.0`
- `TASK-073` Add Product Quotas And Runtime Limits (deprioritized: not justified for single-operator/self use; revisit when going public)
- `TASK-074` Prepare Queue Delivery Abstraction For Broker Migration (deprioritized: DB polling is fine at n=1; evidence-gated)
- `TASK-075` Migrate Operator UI To React (deprioritized: current vanilla JS UI is sufficient for operator self-use)

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
- No repository task is currently marked `in progress`.
- The `v1.0.0` release track is closed.
- The post-release architecture slice through `TASK-072` is merged into `develop`.
- The product-quality track (`TASK-076` through `TASK-081`) is merged into `develop`.

## NEXT

### Immediate
- no task is currently queued as immediate
- `TASK-073` and `TASK-074` are deprioritized (see Planned Post-`v1.0.0`)
- next work will be driven by operator-use feedback on Twitch VOD clip selection
- keep post-release work on top of the checked-in `TASK-081` baseline

### Validation
- `TASK-064` browser E2E suite and CI gate are now present in source.
- `TASK-065` operator runbook and recovery docs are now present in source.
- `TASK-067` backup, restore, export recovery, and post-restore health checks passed on 2026-04-09.

### Post-release
- `TASK-073` should add bounded product-level limits only after the task contract and storage contract stay explicit. Deprioritized: not justified for single-operator/self use; revisit when going public.
- `TASK-074` should prepare broker migration only after the current task-centric delivery contract is stable. Deprioritized: DB polling is fine at n=1; evidence-gated.

### Architectural Follow-Up After `v1.0.0`
- the current `v1.0.0` track hardens the service for a small authenticated operator team
- the next architecture track should build on the now-present task-centric execution model instead of introducing it from scratch
- this follow-up should not preempt the current release gate, but it should already be visible in the source backlog so the project does not drift back into a monolithic `VodJobService` orchestration model

### Release Checklist
- `release-checklist.md` is the gate document used for the controlled `v1.0.0` cut.
- `Documentation/v1.0.0-release.md` is the release record for the `v1.0.0` tag.
- Future stable milestones should continue to record their gate outcome explicitly before merging to `main`.

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
- completed: `TASK-055` Add Authentication And Protected Operator Access
- completed: `TASK-056` Add Security Baseline And Input Hardening
- completed: `TASK-057` Introduce Production Runtime Profiles And Secret Handling
- completed: `TASK-058` Replace MVP Container Strategy And Add Production Edge Runtime
- completed: `TASK-059` Add Health, Readiness, And Worker Diagnostics
- completed: `TASK-060` Harden Queue Reliability And Stuck-Job Recovery
- completed: `TASK-061` Add Operator Recovery Controls
- completed: `TASK-062` Add Metrics And Alertable Observability
- completed: `TASK-063` Add Source And Artifact Retention Cleanup
- completed: `TASK-064` Add Browser E2E Regression And CI Gate
- completed: `TASK-065` Write Operator Runbook, Backup Restore, And Upgrade Notes
- completed: `TASK-067` Run Backup Restore And Rollback Drill
- completed: `TASK-066` Prepare And Execute `v1.0.0` Release

### Post-`v1.0.0` Target Architecture Track
- completed: `TASK-068` Expand Task Model With Retry, Backoff, And Dead-Letter Semantics
- completed: `TASK-069` Extract Task Transition Service And Task-Centric Claim Flow
- completed: `TASK-070` Introduce Dedicated `export-worker` Pool
- completed: `TASK-071` Move Artifact Delivery To Signed URLs And Reduce Backend Media Proxying
- completed: `TASK-072` Make Object Storage The Durable Artifact Contract
- deprioritized: `TASK-073` Add Product Quotas And Runtime Limits
- deprioritized: `TASK-074` Prepare Queue Delivery Abstraction For Broker Migration

### Product Quality Track (Operator Clip-Selection Usability)
- completed: `TASK-076` Add Audio Loudness Signal And Re-Weight Clip Candidate Scoring (`4e722b0`)
- completed: `TASK-077` Make Download Stall Detectable By Gating Heartbeat On Real Progress (`987d710`)
- completed: `TASK-078` Job List/Detail UI: Delete, Bulk Clear, Complete, Static Progress Bar (`b5eb976`)
- completed: `TASK-079` Job Lifecycle On Moderation: Auto-Complete, Manual Complete, Delete From Review (`0dd3992`)
- completed: `TASK-080` Fix Clip Export: Single Muxed Format + Two-Stage Seek + Bounded Threads (`76760e8`)
- completed: `TASK-081` Use TwitchDownloaderCLI For Twitch Sources To Fix A/V Desync (merged into develop)
- perf: yt-dlp concurrent fragment downloads 4 to 8 (`5eaa0bd`)

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
- `TASK-055` through `TASK-065` are now part of the current `develop` baseline.
- `TASK-067` consumed the concrete procedures written in `TASK-065` instead of inventing ad hoc recovery steps.
- `TASK-066` executed only after `TASK-067` evidence was recorded and the accepted drill fix was captured in source.
- `TASK-068`, `TASK-069`, and `TASK-070` are now the checked-in post-release baseline for retry semantics, orchestration extraction, and export-worker routing.
- `TASK-071` and `TASK-072` are now checked in, so the next sequencing decision starts at quotas and broker preparation.
- `TASK-073` should start only after the execution model is explicit enough to enforce concurrency and cost controls coherently. Deprioritized for now: not justified for single-operator/self use.
- `TASK-074` should happen after the current task-centric baseline; broker migration without a clear task contract would just move the current ambiguity into another component. Deprioritized for now: DB polling is fine at n=1.
- `TASK-076` through `TASK-081` form the product-quality track, driven by operator real-use feedback on Twitch VODs. They are all merged into `develop`.

## LATER

### Product Gaps Still Open
- Error handling and observability are still MVP-level, not hardened operations-grade.
- The current pipeline still stops short of the target architecture described for larger-scale operation:
  - no quotas or fairness controls (deprioritized: not needed for single-operator use)
  - no broker-backed queue delivery yet (deprioritized: DB polling is fine at n=1)

### Engineering Follow-Up
- Add a single progress/status document under `agents/` if team workflow needs per-task lifecycle tracking.
- Add decision logging for infrastructure and architecture choices.

## Risks
- `v1.0.0` is released, but the service still carries explicit post-release architecture work.
- Upload ingest now uses the explicit multipart limits from `TASK-040`, and oversized files fail with stable `413` semantics.
- `TASK-044` has been revalidated for malformed URL and `404` cases; residual coverage gaps remain only for timeout and unsupported-source variants, and they are not release blockers for the current gate.
- Restart resilience on export looks acceptable from `TASK-046`: a worker bounce mid-export recovered and completed instead of ghosting the job.
- `TASK-046` also showed that restart recovery is not very observable: file-backed work and exports can continue, but the API may sit on `DOWNLOADING` or `IN_PROGRESS` without an explicit progress signal.
- Worker cold start depends on external model download and is slower without a configured `HF_TOKEN`.
- Very large URL ingests still create long-running `DOWNLOADING` occupancy; this is the direct reason for moving toward a dedicated `download-worker`.
- The dedicated `download-worker` / `processing-worker` / `export-worker` split is now validated in source, but it still lacks quotas, fairness controls, and broker-backed queue delivery.
- Recovery behavior is now documented and exercised for the runbook paths, but not every failure permutation has a dedicated drill.
- `TASK-045` QA found that export retries are allowed, export failure correctly marks the job `FAILED`, and the stale-artifact behavior was addressed in `TASK-054`.
- The current task model is now explicit enough for role-aware pools, but it still has no task priority or fairness input.
- Backend media streaming endpoints still exist for source preview and local-mode export fallback, which is acceptable for the current contract but not the desired long-term general delivery path.

## Recommended Next Sequence
1. Continue operator-use-driven product-quality work as issues surface from real Twitch VOD clip selection.
2. `TASK-073` Add Product Quotas And Runtime Limits (when going public).
3. `TASK-074` Prepare Queue Delivery Abstraction For Broker Migration (when scale evidence justifies it).
4. Keep post-`v1.0.0` work on top of the checked-in `worker_task` / `worker_execution`, durable object-storage, and product-quality (`TASK-081`) baselines.

## Path To Service v1.0.0

Status: completed on 2026-04-09 through `TASK-066`

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
- completed: `TASK-064` Add Browser E2E Regression And CI Gate
- completed: `TASK-065` Write Operator Runbook, Backup Restore, And Upgrade Notes
- completed: `TASK-067` Run Backup Restore And Rollback Drill
- completed: `TASK-066` Prepare And Execute `v1.0.0` Release

### Post-`v1.0.0` Architecture Phase
1. expand the existing task lifecycle with retries, backoff, and dead-letter behavior (done)
2. separate orchestration from `VodJobService` into a dedicated transition layer (done)
3. split export work into its own pool (done)
4. move media delivery toward signed URLs and durable object storage references (done)
5. improve operator clip-selection quality and usability through real-use feedback (done: product-quality track `TASK-076` through `TASK-081`)
6. add quotas and queue-delivery abstraction only after the execution model is stable (deprioritized: not needed for single-operator use)
7. migrate operator UI to React when candidate review or multi-developer frontend work makes local component state unavoidable (`TASK-075`, deprioritized)

### Post-`v1.0.0` Architecture Task Queue
- completed: `TASK-068` Expand Task Model With Retry, Backoff, And Dead-Letter Semantics
- completed: `TASK-069` Extract Task Transition Service And Task-Centric Claim Flow
- completed: `TASK-070` Introduce Dedicated `export-worker` Pool
- completed: `TASK-071` Move Artifact Delivery To Signed URLs And Reduce Backend Media Proxying
- completed: `TASK-072` Make Object Storage The Durable Artifact Contract
- deprioritized: `TASK-073` Add Product Quotas And Runtime Limits
- deprioritized: `TASK-074` Prepare Queue Delivery Abstraction For Broker Migration
- deprioritized: `TASK-075` Migrate Operator UI To React

  **Do not start until at least one of the following is true:**
  - Candidate review needs per-candidate local state (scrubber, inline approval, clip preview). The current full-`innerHTML` re-render strategy kills local component state on every 5-second poll; React's reconciliation makes this tractable.
  - A second developer joins frontend work. Template-literal rendering does not scale across contributors.
  - A third interactive panel is needed on the job detail page where optimistic UI or local-only transitions are required.

  **Not a trigger on its own:** file size, "feels like vanilla", or adding a stage progress bar. Those are solvable with targeted DOM patching and a local interval without a framework migration.

  **Scope when the time comes:** Preact is the low-overhead entry point if the backend stays Spring and there is no build infrastructure yet. Full React + Vite is the right call if TypeScript is introduced at the same time.

### Product Quality Track (Operator Clip-Selection Usability)
- completed: `TASK-076` Add Audio Loudness Signal And Re-Weight Clip Candidate Scoring (`4e722b0`)
- completed: `TASK-077` Make Download Stall Detectable By Gating Heartbeat On Real Progress (`987d710`)
- completed: `TASK-078` Job List/Detail UI: Delete, Bulk Clear, Complete, Static Progress Bar (`b5eb976`)
- completed: `TASK-079` Job Lifecycle On Moderation: Auto-Complete, Manual Complete, Delete From Review (`0dd3992`)
- completed: `TASK-080` Fix Clip Export: Single Muxed Format + Two-Stage Seek + Bounded Threads (`76760e8`)
- completed: `TASK-081` Use TwitchDownloaderCLI For Twitch Sources To Fix A/V Desync (merged into develop)

### Critical Path To v1.0.0
1. Finish the current release baseline and move it intentionally to `main`.
2. Add auth and security hardening before treating the product as a real service.
3. Replace local-first runtime compromises with a deployable runtime package.
4. Close reliability and operator-control gaps so failed jobs are recoverable and diagnosable.
5. Lock the service down with CI, browser E2E, runbooks, and an actual restore drill before the controlled release.
