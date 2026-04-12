# Operator Runbook

## Release Validation Status

1. `TASK-067` rerun passed on 2026-04-09 against the checked-in runtime shape used for `v1.0.0`.
2. Recorded evidence lives under `C:\Users\Peatr\AppData\Local\Temp\streamcut-task067-rerun`:
   - `job1.json`
   - `job1-export.json`
   - `restore-check.json`
   - `job2.json`
   - `recovery-check.json`
3. The recorded outcomes cover backup, restore, export recovery, post-restore health checks, and a second successful job/export after restore.

## 1. Service Overview

1. Preconditions: This runbook covers the checked-in Docker runtime. `docker-compose.yml` is the local full-stack path with `postgres` and `minio`. `docker-compose.production.yml` is the production-oriented package with `edge`, `backend`, `download-worker`, and `processing-worker`, and it expects PostgreSQL and S3-compatible artifact storage to be provided separately through environment variables.
2. Service purpose: StreamCut accepts a VOD URL or uploaded video, runs download and analysis work through background workers, lets a single operator review the generated clip candidates, and exports approved clips to S3-compatible artifact storage for download.
3. Components and roles:
   - `edge`: Caddy reverse proxy that publishes the production package on `EDGE_PORT` and forwards traffic to `backend`.
   - `backend`: Spring Boot application that serves the UI and API, applies Liquibase migrations on startup, stores job metadata in PostgreSQL, exposes `/health`, `/health/ready`, `/health/workers`, and `/actuator/prometheus`, and owns retention cleanup.
   - `download-worker`: pulls queued download work and writes source videos under `APP_STORAGE_LOCAL_ROOT`.
   - `processing-worker`: runs analysis and export work, uses `/model-cache` for the transcription model cache, and uploads finished export artifacts to S3-compatible storage.
   - `postgres`: present in the local full-stack compose file only; stores jobs, candidates, events, executions, and retention state.
   - `minio`: present in the local full-stack compose file only; acts as the S3-compatible artifact store for exported clips.
4. Data flow summary:
   - operator signs in with `APP_OPERATOR_USERNAME` and `APP_OPERATOR_PASSWORD`
   - backend stores job metadata in PostgreSQL and queues download work
   - `download-worker` fetches the source video and writes it to the local storage root
   - backend queues processing work
   - `processing-worker` produces transcripts, analysis windows, and clip candidates
   - operator reviews candidates in the UI and starts an export
   - `processing-worker` exports the approved clip and writes the artifact to S3-compatible storage

## 2. Starting The Service

1. Preconditions: Docker Engine and the Docker Compose plugin are installed, the repository is checked out on the target host, external PostgreSQL and S3-compatible artifact storage are reachable for the production package, and an environment file such as `env.production` exists.
2. First-time setup:
   - copy `env.production.example` to `env.production`
   - set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_OPERATOR_USERNAME`, `APP_OPERATOR_PASSWORD`, `APP_ARTIFACT_STORAGE_*`, and `APP_STORAGE_LOCAL_ROOT=/data/storage`
   - keep the same `APP_STORAGE_LOCAL_ROOT` value for `backend`, `download-worker`, and `processing-worker`
   - if you need source videos to survive container replacement, back the chosen storage root with durable host storage before first start
3. Volume setup: no manual volume creation is required. Compose creates the declared named volumes on first start. In the production package these are `streamcut-data` and `streamcut-hf-cache`. In the local full-stack file they also include `streamcut-postgres` and `streamcut-minio`.
4. Start the production package from the repository root:

```bash
docker compose -f compose.streamcut.yml --env-file env.production up -d --build
```

5. If the same variables are already exported in the shell or provided through `.env`, the shorter start command is equivalent:

```bash
docker compose -f compose.streamcut.yml up -d
```

6. Verify startup. Replace port `80` if `EDGE_PORT` was changed:

```bash
docker compose -f compose.streamcut.yml ps
curl http://localhost:80/health
curl http://localhost:80/health/ready
curl http://localhost:80/health/workers
```

7. Expected startup results:
   - `/health` returns `{"status":"UP"}`
   - `/health/ready` returns `{"status":"READY"}`
   - `/health/workers` returns JSON with top-level `"status":"UP"` and `download` and `processing` entries under `roles`
   - the `backend`, `download-worker`, and `processing-worker` containers stay running instead of restarting
8. Common startup failures and fixes:
   - backend exits on boot with missing configuration: re-check `env.production`, especially `SPRING_DATASOURCE_*`, `APP_OPERATOR_*`, and `APP_ARTIFACT_STORAGE_*`
   - backend never becomes healthy: verify PostgreSQL reachability, credentials, and network routing for `SPRING_DATASOURCE_URL`
   - backend fails in S3 mode: verify `APP_ARTIFACT_STORAGE_ENDPOINT`, `APP_ARTIFACT_STORAGE_PUBLIC_ENDPOINT`, `APP_ARTIFACT_STORAGE_ACCESS_KEY`, `APP_ARTIFACT_STORAGE_SECRET_KEY`, and `APP_ARTIFACT_STORAGE_BUCKET`
   - workers stay down or idle: verify the backend is healthy and the same `APP_STORAGE_LOCAL_ROOT` is configured for all three application containers
   - port bind failure on `edge`: change `EDGE_PORT` or stop the process already bound to that host port
   - first processing run is much slower than expected: the transcription model is downloaded into `/model-cache` on cold start

## 3. Health And Readiness Checks

1. Preconditions: the stack is running and the backend is reachable through `edge` or directly on the backend port.
2. Check process health:

```bash
curl http://localhost:80/health
```

3. Expected response:

```json
{"status":"UP"}
```

4. Check backend readiness:

```bash
curl http://localhost:80/health/ready
```

5. Expected response:

```json
{"status":"READY"}
```

6. Check worker diagnostics:

```bash
curl http://localhost:80/health/workers
```

7. Read the worker diagnostics fields as follows:
   - `status`: overall diagnostics endpoint status; current expected value is `UP`
   - `staleTimeoutSec`: default heartbeat timeout in seconds for task types without an override
   - `staleTimeoutsSec`: per-task heartbeat timeout map; by default `ANALYZE` is longer than the default timeout
   - `reconcileIntervalSec`: how often stale execution recovery runs
   - `roles.download` and `roles.processing`: per-role snapshots
   - `queued`: tasks waiting for a worker in that role
   - `claimed`: tasks a worker has claimed but not yet moved to `RUNNING`
   - `running`: tasks currently executing in that role
   - `stale`: active tasks whose `oldestHeartbeatAt` is older than the applicable stale timeout
   - `oldestHeartbeatAt`: oldest active heartbeat timestamp seen for that role; `null` means no active heartbeat is present
   - `queuedByTaskType`: queued counts split by task type
   - `activeByTaskType`: claimed and running counts split by task type
8. Be concerned when:
   - any role reports `stale > 0`
   - `oldestHeartbeatAt` is older than the applicable timeout from `staleTimeoutsSec` or, if there is no override, `staleTimeoutSec`
   - `queued > 0` remains high while both `claimed` and `running` stay at `0`
   - the endpoint stays `UP` but jobs are not moving and the same job status remains unchanged in the UI

## 4. Backup Procedures

1. Preconditions: take backups during a low-activity window, write them to a directory outside container filesystems, and copy the finished backup set off-host. The production compose file does not define `postgres` or `minio`, so the `docker exec` and `mc` examples below apply only when those services are operator-managed containers.
2. Back up PostgreSQL with `pg_dump` when PostgreSQL runs in Docker:
   - find the container name with `docker compose ps postgres`
   - run `docker exec -t <postgres-container> pg_dump -U streamcut -d streamcut -Fc > backups/streamcut_YYYYMMDD_HHMM.dump`
   - keep at least one daily backup and always take an extra backup immediately before upgrades
3. If the production package points to an external PostgreSQL host instead of a container, run the equivalent `pg_dump` against the host from a trusted admin machine. The backup target is still the database named by `SPRING_DATASOURCE_URL`.
4. Back up MinIO when artifact storage is MinIO:
   - configure the MinIO Client alias with `mc alias set streamcut http://127.0.0.1:9000 <access-key> <secret-key>`
   - mirror the bucket with `mc mirror --overwrite streamcut/<bucket> backups/minio/<bucket>/`
   - store the mirrored bucket off-host or snapshot the MinIO data volume while writes are stopped
5. If `APP_ARTIFACT_STORAGE_*` points at another S3-compatible service, use that provider's bucket backup, snapshot, or versioning workflow instead of a local MinIO volume backup.
6. Source video storage is not backed up by default. Source videos live under `APP_STORAGE_LOCAL_ROOT` on the local filesystem and the expected recovery path is to re-upload or recreate them if the host is lost.
7. What is not backed up by the default procedure and why:
   - local source videos under `APP_STORAGE_LOCAL_ROOT`, because this runbook treats them as re-uploadable inputs rather than durable system-of-record data
   - transient worker-local files under the same storage root, because they can be regenerated from the database state and source inputs
   - the transcription model cache under `/model-cache`, because it is repopulated on demand
   - `env.production`, secrets, and container images, because they are deployment inputs that must be managed separately from runtime data

## 5. Restore Procedures

1. Preconditions: schedule downtime, stop application writes, have a matching PostgreSQL dump and artifact backup set ready, and remember that source videos are only restorable if you captured a separate filesystem backup of `APP_STORAGE_LOCAL_ROOT`.
2. Stop the application stack before restore work:

```bash
docker compose -f compose.streamcut.yml --env-file env.production down
```

3. Restore PostgreSQL when it is container-managed:
   - start only the database container or companion database stack
   - if the `streamcut` database already exists, empty it or recreate it
   - restore the dump with `docker exec -i <postgres-container> pg_restore -U streamcut -d streamcut --clean --if-exists < backups/streamcut_YYYYMMDD_HHMM.dump`
4. If PostgreSQL is external in production, run the equivalent `pg_restore` against the external database host instead of a container. Restore into the database referenced by `SPRING_DATASOURCE_URL`.
5. Restore MinIO when artifacts were backed up from MinIO:
   - start MinIO or the companion MinIO stack
   - configure the client alias with `mc alias set streamcut http://127.0.0.1:9000 <access-key> <secret-key>`
   - restore the bucket with `mc mirror --overwrite backups/minio/<bucket>/ streamcut/<bucket>/`
   - if you used a volume snapshot instead of `mc mirror`, restore the MinIO data volume while MinIO is stopped
6. Restore source videos only if you took a separate filesystem backup of `APP_STORAGE_LOCAL_ROOT`. There is no default source-video restore path in this repository.
7. Start the application stack after data restore:

```bash
docker compose -f compose.streamcut.yml --env-file env.production up -d --build
```

8. Liquibase schema migration runs automatically on backend startup. No separate database migration command is required after restore.
9. Verify the restore:
   - check `curl http://localhost:80/health`
   - check `curl http://localhost:80/health/ready`
   - check `curl http://localhost:80/health/workers`
   - sign in at `/login.html` and confirm the job list loads on `/index.html`
   - open a known job and confirm expected events, candidates, and export records are present

## 6. Upgrade Procedures

1. Preconditions: announce a maintenance window, take fresh database and artifact backups, and make sure the target release source or images are available on the host.
2. Update the deployment bits:
   - if you deploy from repository source, pull the new revision into the checkout
   - if you deploy from prebuilt images, pull the newer images before restart
3. Rebuild and restart the production package:

```bash
docker compose -f compose.streamcut.yml --env-file env.production up -d --build
```

4. Liquibase migrations run automatically when the new backend starts. There is no manual upgrade migration step in the runbook.
5. Verify the upgraded stack:
   - `curl http://localhost:80/health`
   - `curl http://localhost:80/health/ready`
   - `curl http://localhost:80/health/workers`
   - sign in and confirm the job list and a known job detail page load successfully
6. Roll back only by returning to the previous application version and restoring the pre-upgrade backup set. There is no automatic schema rollback in `v1.0.0`.
7. Zero-downtime upgrades are not supported in `v1.0.0`. Plan for an in-place restart and a maintenance window for every production upgrade.

## 7. Worker Diagnostics And Recovery

1. Preconditions: the stack is running, `/health/workers` is reachable, and an operator can sign in to the UI for manual recovery actions.
2. Tell whether a worker role is alive:
   - call `/health/workers`
   - confirm the role exists under `roles`
   - confirm `stale` is `0`
   - during active work, expect recent `oldestHeartbeatAt` values and at least one `claimed` or `running` task
3. Tell whether a job is stuck:
   - `/health/workers` reports `stale > 0`
   - `oldestHeartbeatAt` is older than the applicable timeout
   - the job detail page shows an unchanged active status for longer than the worker timeout window
   - queue counts keep growing while active counts do not move
4. Use `Retry` from the UI only for `FAILED` jobs. It increments the processing version, clears prior analysis artifacts, and requeues the job from the download stage.
5. Use `Cancel` from the UI only for `QUEUED_FOR_DOWNLOAD` or `QUEUED_FOR_PROCESSING`. It is the operator path for stopping queued work before a worker picks it up.
6. Use `Force Fail` from the UI only for active worker states such as `DOWNLOADING`, `EXTRACTING_AUDIO`, `TRANSCRIBING`, `DETECTING_SILENCE`, `ANALYZING_WINDOWS`, `GENERATING_CANDIDATES`, or `EXPORTING_CLIP`. It records an explicit operator failure event and leaves the job in `FAILED`.
7. Use `Delete` from the UI only for terminal jobs in `COMPLETED`, `FAILED`, or `CANCELED` status. It permanently removes the job record, all pipeline data, and any stored source video and export artifacts. There is no undo. Use it to clean up finished or failed jobs that are no longer needed.
8. After any recovery action, refresh the job page and confirm the latest event shows `JOB_RETRIED`, `JOB_CANCELED`, or `JOB_FORCE_FAILED` as expected.

## 8. Retention And Cleanup

1. Preconditions: the backend is running with `APP_RETENTION_CLEANUP_ENABLED=true`, or the variable is absent and defaults are in effect.
2. Automatic cleanup behavior:
   - source videos for `COMPLETED` and `FAILED` jobs are eligible for cleanup 7 days after the terminal timestamp
   - export artifacts for `COMPLETED` jobs are eligible for cleanup 30 days after completion
   - the cleanup scheduler runs nightly at `03:00 UTC` by default
3. Verify that cleanup ran:
   - inspect backend logs for the marker `retention_cleanup_completed`
   - expect the log line to include `sourceFilesCleaned=` and `artifactFilesCleaned=`
4. Disable cleanup by setting `APP_RETENTION_CLEANUP_ENABLED=false` in the active environment file and restarting the backend.
5. Change retention windows by setting `APP_RETENTION_SOURCE_RETENTION` and `APP_RETENTION_ARTIFACT_RETENTION`, then restart the backend so the new values are loaded.
6. Change the schedule only if needed by setting `APP_RETENTION_CLEANUP_CRON` and `APP_RETENTION_CLEANUP_ZONE`, then restart the backend.
7. Remember that export downloads stop working after artifact cleanup removes the stored artifact reference for an expired completed export.

## 9. Known Limitations And Risks

1. The service is single-tenant. One operator credential set is configured through `APP_OPERATOR_USERNAME` and `APP_OPERATOR_PASSWORD`.
2. Zero-downtime upgrades are not supported in `v1.0.0`.
3. Source videos are not backed up by default.
4. The first processing run on a cold host requires a large model download into `/model-cache`, which is roughly 1.5 GB.
5. There is no automatic retry policy for terminal job failures. Operators must use the UI to retry failed jobs manually.
