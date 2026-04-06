# Runtime

## Upload Policy
This is the explicit MVP upload policy for `POST /api/jobs/upload`.

- Upload mode: single-file upload only.
- Maximum file size: `512 MB`.
- Maximum request size: `520 MB` to allow multipart overhead.
- Supported upload formats:
  - `video/mp4` (`.mp4`)
  - `video/quicktime` (`.mov`)
  - `video/x-matroska` (`.mkv`)
  - `video/webm` (`.webm`)
  - `video/x-msvideo` (`.avi`)
  - `video/mpeg` (`.mpeg`, `.mpg`)
- User-facing behavior when the limit is exceeded:
  - the backend should return a stable `413 Payload Too Large` response
  - the error body should say that the upload exceeds the `512 MB` limit
  - the UI should keep the current form state, show the message inline, and avoid creating a job
- Policy status: this limit set is MVP-intended, not temporary. It is the release policy until a later explicit policy change.
- If future release work changes the upload envelope, update this section first and keep backend config, API docs, backlog notes, and UI copy aligned with it.

## Local Startup
1. Build and start everything with `docker compose up --build`.
2. Wait for PostgreSQL health checks to pass.
3. Open the backend on `http://localhost:8080`.
4. Use `docker compose logs -f backend worker postgres` to follow startup and runtime output.

## Required Environment
Backend:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/streamcut`
- `SPRING_DATASOURCE_USERNAME=streamcut`
- `SPRING_DATASOURCE_PASSWORD=streamcut`
- `APP_STORAGE_LOCAL_ROOT=/data/storage`

Worker:
- `APP_STORAGE_LOCAL_ROOT=/data/storage`
- `PYTHONUNBUFFERED=1`

PostgreSQL:
- `POSTGRES_DB=streamcut`
- `POSTGRES_USER=streamcut`
- `POSTGRES_PASSWORD=streamcut`

## Logging Expectations
- Backend and worker must log to stdout only in local runtime.
- Keep log output plain and readable; no file-based logging is required for MVP.
- Startup logs should make it obvious which service is running and whether dependencies are ready.
- PostgreSQL readiness is verified through its healthcheck, not through ad-hoc manual checks.
- Job lifecycle logs should be structured enough to trace one job end-to-end by `jobId` and stage without reading every line of output.
- Failure logs should include the affected stage or `failedState` where applicable so QA can correlate backend state with worker output.
- Use `jobId=` in backend and worker logs as the primary trace key for one pipeline run.
- Expected lifecycle markers include `job_created`, `job_queued`, `job_claimed`, `job_result_ingested`, `export_started`, `export_completed`, and `job_failed`.

## Cleanup Policy
- Temporary build artifacts must stay out of git. The existing `.dockerignore` files already exclude common caches and build outputs.
- Runtime data lives under the shared `/data` volume. If you need a clean slate, run `docker compose down -v` to remove volumes and then start again.
- Generated media, exports, and local storage artifacts should be treated as disposable runtime data, not source-controlled files.
- Python caches and test caches remain ignored inside `worker/`.

## Service Notes
- Backend and worker share the same local data volume so future media and export steps can use one predictable artifact root.
- PostgreSQL is a named volume, so local data persists across `docker compose down` and only resets with `-v`.
- The worker container is isolated from the backend process and is expected to communicate through future integration points, not direct code coupling.
- The compose setup is intentionally local-first and should remain simple until the MVP stabilizes.

## Docker Runtime Notes
- The current Docker image strategy is acceptable for the MVP, but it is intentionally not production-grade.
- `Dockerfile.backend` and `worker/Dockerfile` currently use `postgres:15` as a base image and layer the runtime they need on top of it.
- That choice is a convenience compromise for the current MVP. It keeps the stack reproducible and already validated locally, but it should be revisited before any real deployment hardening.
- MinIO is the artifact store for exports in the local Docker stack, and the backend is configured to talk to it via the internal service endpoint while serving public artifact URLs back through `localhost:9000`.
- Named volumes are part of the runtime contract:
  - `streamcut-postgres` holds the database state
  - `streamcut-data` holds shared media and export files
  - `streamcut-minio` holds MinIO object storage data
- Contributors should treat `docker compose down -v` as the explicit cleanup/reset path when they need a clean slate.
