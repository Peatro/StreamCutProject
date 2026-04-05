# Runtime

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
