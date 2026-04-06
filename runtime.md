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
5. This path is explicitly local-only and uses `docker-compose.yml`.

## Runtime Profiles
- `local` is the default Spring profile and is the profile used by `docker-compose.yml`.
- `prod` is the explicit deployment profile for `v1.0.0` and later service environments.
- Checked-in defaults are allowed only for the `local` profile.
- Production secrets and deployment-specific values must come from environment variables or the deploy system, not from repo-managed source files.

## Local Runtime Contract
Backend:
- `SPRING_PROFILES_ACTIVE=local`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/streamcut`
- `SPRING_DATASOURCE_USERNAME=streamcut`
- `SPRING_DATASOURCE_PASSWORD=streamcut`
- `APP_STORAGE_LOCAL_ROOT=/data/storage`
- `APP_OPERATOR_USERNAME=operator`
- `APP_OPERATOR_PASSWORD=operator-password`
- `APP_ARTIFACT_STORAGE_MODE=S3`
- `APP_ARTIFACT_STORAGE_ENDPOINT=http://minio:9000`
- `APP_ARTIFACT_STORAGE_PUBLIC_ENDPOINT=http://localhost:9000`
- `APP_ARTIFACT_STORAGE_REGION=us-east-1`
- `APP_ARTIFACT_STORAGE_ACCESS_KEY=minioadmin`
- `APP_ARTIFACT_STORAGE_SECRET_KEY=minioadmin`
- `APP_ARTIFACT_STORAGE_BUCKET=streamcut-artifacts`

Worker:
- `APP_STORAGE_LOCAL_ROOT=/data/storage`
- `PYTHONUNBUFFERED=1`

PostgreSQL:
- `POSTGRES_DB=streamcut`
- `POSTGRES_USER=streamcut`
- `POSTGRES_PASSWORD=streamcut`

## Production Runtime Contract
- The backend must run with `SPRING_PROFILES_ACTIVE=prod`.
- Production runtime values must be supplied explicitly; `application-prod.yaml` no longer carries repo-managed fallback credentials or storage paths.
- The current example contract lives in `env.production.example`.
- The production compose entrypoint is `docker-compose.production.yml`.
- The public entry path is the `edge` container on port `80` by default; `backend` is internal-only behind the reverse proxy.
- The production package keeps PostgreSQL and S3-compatible object storage as external runtime dependencies and does not expose a database container publicly.
- Required backend environment for `prod`:
  - `EDGE_PORT` if the reverse proxy should listen on a host port other than `80`
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `APP_STORAGE_LOCAL_ROOT`
  - `APP_OPERATOR_USERNAME`
  - `APP_OPERATOR_PASSWORD`
  - `APP_ARTIFACT_STORAGE_MODE`
  - `APP_ARTIFACT_STORAGE_ENDPOINT`
  - `APP_ARTIFACT_STORAGE_PUBLIC_ENDPOINT`
  - `APP_ARTIFACT_STORAGE_ACCESS_KEY`
  - `APP_ARTIFACT_STORAGE_SECRET_KEY`
  - `APP_ARTIFACT_STORAGE_BUCKET`
- Optional production backend environment with intentional defaults:
  - `SPRING_DATASOURCE_DRIVER_CLASS_NAME` defaults to `org.postgresql.Driver`
  - `APP_ARTIFACT_STORAGE_REGION` defaults to `us-east-1`
  - `APP_ARTIFACT_STORAGE_PRESIGN_TTL` defaults to `15m`
- The production compose path is expected to be run like:
  - `docker compose -f docker-compose.production.yml --env-file env.production up --build -d`
- In `S3` mode, missing endpoint, public endpoint, access key, secret key, or bucket should now fail startup instead of silently falling back.

## Logging Expectations
- Backend and worker must log to stdout only in local runtime.
- Keep log output plain and readable; no file-based logging is required for MVP.
- Startup logs should make it obvious which service is running and whether dependencies are ready.
- PostgreSQL readiness is verified through its healthcheck, not through ad-hoc manual checks.
- Job lifecycle logs should be structured enough to trace one job end-to-end by `jobId` and stage without reading every line of output.
- Failure logs should include the affected stage or `failedState` where applicable so QA can correlate backend state with worker output.
- Use `jobId=` in backend and worker logs as the primary trace key for one pipeline run.
- Expected lifecycle markers include `job_created`, `job_queued`, `job_claimed`, `job_result_ingested`, `export_started`, `export_completed`, and `job_failed`.

## Operator Authentication
- Operator access uses Spring Security form login with a session cookie.
- The public bootstrap page is `/login.html`, which fetches `/csrf` and posts credentials to `/login`.
- Operator credentials are configured through environment variables in both profiles:
  - `APP_OPERATOR_USERNAME`
  - `APP_OPERATOR_PASSWORD`
- Protected surfaces include the dashboard pages, operator-facing `/api/**` endpoints, and artifact download/stream endpoints.
- `/health` stays public.
- `/api/internal/worker/**` stays public for now so worker transport is not blocked in `TASK-055`; machine auth can be handled in `TASK-056`.
- The static frontend sends `X-XSRF-TOKEN` on operator POST requests after bootstrapping the CSRF token from `/csrf`.

## Input Hardening
- URL ingest only accepts absolute `http` or `https` URLs.
- Worker callback paths for local video, audio, and export artifacts must stay under the configured local storage root.
- Local storage path helpers reject outside-root paths instead of silently normalizing them into acceptance.
- Worker namespace machine auth is still intentionally deferred; if that changes, document it before tightening the public worker routes.

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
- `Dockerfile.backend` now builds the Spring Boot jar in a Gradle build stage and runs it on `eclipse-temurin:21-jre`.
- `worker/Dockerfile` now runs on `python:3.12-slim` with only the packages it needs for the worker process.
- `docker-compose.yml` remains the local full-stack path, while `docker-compose.production.yml` defines the production-oriented package with an explicit edge runtime.
- `docker-compose.yml` is explicitly the local-runtime path; it should not be treated as a production deploy manifest.
- `deploy/Caddyfile` defines the minimal production edge runtime and routes public UI/API traffic to `backend:8080`.
- MinIO is the artifact store for exports in the local Docker stack, and the backend is configured to talk to it via the internal service endpoint while serving public artifact URLs back through `localhost:9000`.
- Named volumes are part of the runtime contract:
  - `streamcut-postgres` holds the database state
  - `streamcut-data` holds shared media and export files
  - `streamcut-minio` holds MinIO object storage data
- Contributors should treat `docker compose down -v` as the explicit cleanup/reset path when they need a clean slate.
