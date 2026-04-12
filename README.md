# StreamCut

StreamCut is an operator-facing VOD clipping service.

It accepts a video URL or an uploaded file, runs a background media-processing pipeline, proposes clip candidates, and lets an authenticated operator review and export approved clips.

Release status: `v1.0.0`

## What The Project Does

- Provides a browser UI for job submission, job monitoring, candidate review, and export management.
- Runs download and processing work asynchronously through dedicated workers.
- Stores job state, task state, events, transcripts, silence segments, analysis windows, and clip candidates in PostgreSQL.
- Stores source material on a shared local storage root and exports in S3-compatible artifact storage.
- Exposes health checks, worker diagnostics, and Prometheus metrics for operations.
- Applies retention cleanup for old source files and exported artifacts.

## End-To-End Flow

1. An operator signs in and submits a VOD URL or uploads a local video.
2. The backend creates a job and queues a download task.
3. The `download-worker` fetches the source video into the shared storage root.
4. The backend queues processing work.
5. The `processing-worker` extracts audio, transcribes speech, detects silence, analyzes windows, and generates non-overlapping clip candidates.
6. The operator reviews candidates in the UI and approves, rejects, or exports clips.
7. Exported clips are uploaded to S3-compatible artifact storage and exposed back through the backend.

## Architecture At A Glance

```text
Operator Browser
  -> Spring Boot backend
     -> PostgreSQL (jobs, tasks, events, candidates, transcripts)
     -> shared local storage root (source video, audio, working files)
     -> S3-compatible artifact storage (exported clips)

download-worker
  -> claims DOWNLOAD work from backend
  -> writes source video to shared storage

processing-worker
  -> claims ANALYZE and EXPORT work from backend
  -> runs ffmpeg + faster-whisper pipeline
  -> uploads exported clips to artifact storage
```

## Main Components

- `backend`
  Spring Boot 4 application that serves the UI, API, authentication, Liquibase migrations, health endpoints, metrics, retention cleanup, and worker coordination.
- `download-worker`
  Python worker that materializes source videos from submitted URLs.
- `processing-worker`
  Python worker that runs transcription, silence detection, candidate analysis, and clip export.
- `postgres`
  Primary relational store for runtime state.
- `minio`
  Local S3-compatible artifact store in the development Docker stack.
- `edge`
  Caddy reverse proxy used in the production-oriented compose package.

## Repository Layout

```text
src/main/java/              Spring Boot backend
src/main/resources/static/  Browser UI
src/test/java/              Backend and integration tests
src/e2eTest/java/           Browser E2E tests with Selenide
worker/                     Python worker package
Documentation/              Runbook, runtime docs, release notes, ADRs
deploy/                     Production edge configuration
docker-compose.yml          Local full stack
docker-compose.production.yml
                            Production-oriented package
```

## Local Quick Start

### Prerequisites

- Docker Engine
- Docker Compose plugin

### Start The Full Local Stack

```bash
docker compose up -d --build
```

This starts:

- `postgres`
- `minio`
- `backend`
- `download-worker`
- `processing-worker`

### Open The Application

- UI: `http://localhost:8080/login.html`
- Default local credentials:
  - username: `operator`
  - password: `operator-password`
- MinIO console: `http://localhost:9001`
  - username: `minioadmin`
  - password: `minioadmin`

### Useful Local Checks

```bash
curl http://localhost:8080/health
curl http://localhost:8080/health/ready
curl http://localhost:8080/health/workers
```

### Reset Local Runtime Data

```bash
docker compose down -v
```

This removes the local named volumes, including PostgreSQL data, MinIO data, shared media files, and the worker model cache.

## Running From Source

### Prerequisites

- Java 21
- Python 3.11+ for worker development
- `ffmpeg` if you run workers outside Docker

### Backend

```bash
./gradlew test
./gradlew bootRun
```

Notes:

- The default Spring profile is `local`.
- By default the backend expects PostgreSQL at `jdbc:postgresql://localhost:5432/streamcut`.
- The simplest contributor workflow is still the Docker-based local stack above.

### Worker

```bash
cd worker
python -m venv .venv
# activate the virtualenv in your shell
pip install -e .
python -m streamcut_worker
```

The worker reads configuration from environment variables such as:

- `BACKEND_BASE_URL`
- `APP_STORAGE_LOCAL_ROOT`
- `WORKER_ID`
- `WORKER_ROLE`
- `HF_HOME`

## Testing

### Backend Test Suite

```bash
./gradlew test
```

### Browser E2E Suite

Start the backend first with the `local` profile, then run:

```bash
./gradlew e2eTest
```

For local browser debugging:

```bash
./gradlew e2eTest -Dselenide.headless=false
```

Important:

- Run browser E2E tests against the backend only.
- Do not start `download-worker` or `processing-worker` while running local E2E tests, otherwise queued fixture jobs can be claimed before assertions run.

### Worker Tests

```bash
python -m unittest discover -s worker/tests -p "test_*.py"
```

### CI Shape

GitHub Actions currently runs:

- `./gradlew test`
- `./gradlew e2eTest` against a locally started backend with PostgreSQL

## Key Runtime Configuration

The most important environment variables are:

| Area | Variables |
|---|---|
| Database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Operator auth | `APP_OPERATOR_USERNAME`, `APP_OPERATOR_PASSWORD` |
| Shared storage | `APP_STORAGE_LOCAL_ROOT` |
| Artifact storage | `APP_ARTIFACT_STORAGE_MODE`, `APP_ARTIFACT_STORAGE_ENDPOINT`, `APP_ARTIFACT_STORAGE_PUBLIC_ENDPOINT`, `APP_ARTIFACT_STORAGE_ACCESS_KEY`, `APP_ARTIFACT_STORAGE_SECRET_KEY`, `APP_ARTIFACT_STORAGE_BUCKET` |
| Worker runtime | `BACKEND_BASE_URL`, `WORKER_ID`, `WORKER_ROLE`, `HF_HOME` |
| Retention | `APP_RETENTION_CLEANUP_ENABLED`, `APP_RETENTION_SOURCE_RETENTION`, `APP_RETENTION_ARTIFACT_RETENTION` |

Profile defaults:

- `local`
  uses checked-in development defaults and is the default Spring profile
- `prod`
  requires explicit environment configuration and is intended for deployed environments

## Production-Oriented Package

The checked-in production package is `docker-compose.production.yml`.

It includes:

- `edge`
- `backend`
- `download-worker`
- `processing-worker`

It expects these dependencies to be provided externally:

- PostgreSQL
- S3-compatible artifact storage

### Start Production Package

```bash
cp env.production.example env.production
docker compose -f compose.streamcut.yml --env-file env.production up -d --build
```

By default the public entrypoint is the `edge` container on port `80`.

## API And UI Surface

### UI Pages

- `/login.html`
- `/index.html`
- `/job.html?id={jobId}`

### Operator API

- `POST /api/jobs/url`
- `POST /api/jobs/upload`
- `GET /api/jobs`
- `GET /api/jobs/{id}`
- `GET /api/jobs/{id}/candidates`
- `GET /api/jobs/{id}/events`
- `POST /api/jobs/{id}/retry`
- `POST /api/jobs/{id}/cancel`
- `POST /api/jobs/{id}/force-fail`
- `POST /api/candidates/{id}/approve`
- `POST /api/candidates/{id}/reject`
- `POST /api/candidates/{id}/export`

### Public Health And Diagnostics

- `GET /health`
- `GET /health/ready`
- `GET /health/workers`

### Operator-Authenticated Metrics

- `GET /actuator/metrics`
- `GET /actuator/prometheus`

## Operational Notes

- Upload size policy is `512 MB` max file size and `520 MB` max request size.
- Local runtime uses MinIO as the S3-compatible artifact store.
- The first processing run on a cold host may take longer because the transcription model has to be downloaded into the worker cache.
- Retention cleanup is enabled by default:
  - source files: 7 days after terminal state
  - artifacts: 30 days after completion

## Known Limitations In `v1.0.0`

- Single-tenant service with one configured operator credential set.
- Zero-downtime upgrades are not supported.
- Source videos under `APP_STORAGE_LOCAL_ROOT` are not backed up by default.
- Automatic retry, backoff, and dead-letter handling are not implemented yet.
- Export still shares the processing worker pool instead of using a dedicated export worker.

## Documentation Map

Start here for deeper project details:

- [Documentation/doc-index.md](Documentation/doc-index.md) - documentation map
- [Documentation/runtime.md](Documentation/runtime.md) - runtime assumptions and configuration
- [Documentation/runbook.md](Documentation/runbook.md) - operations, backup, restore, upgrade
- [Documentation/STORAGE.md](Documentation/STORAGE.md) - storage layout and constraints
- [Documentation/architecture-roadmap.md](Documentation/architecture-roadmap.md) - post-`v1.0.0` direction
- [Documentation/v1.0.0-release.md](Documentation/v1.0.0-release.md) - release notes and limitations
- [Documentation/adr/README.md](Documentation/adr/README.md) - architectural decision records
