# Local Compose

## Run
```bash
docker compose up --build
```

## Run With GPU Processing
```bash
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up --build
```

## Production Package
```bash
docker compose -f compose.streamcut.yml --env-file env.production up --build -d
```

## Services
- `backend` on `http://localhost:8080`
- `postgres` on `localhost:5432`
- `download-worker` runs the Python worker entrypoint for `DOWNLOAD` tasks
- `processing-worker` runs the Python worker entrypoint for `ANALYZE` tasks
- `export-worker` runs the Python worker entrypoint for `EXPORT` tasks

## Runtime Notes
- `docker-compose.yml` is the explicit local-runtime contract and now starts the backend with `SPRING_PROFILES_ACTIVE=local`.
- `docker-compose.gpu.yml` is an optional local override that swaps only the processing worker to `worker/Dockerfile.gpu`, sets `WHISPER_DEVICE=cuda`, and requests one NVIDIA GPU.
- `docker-compose.production.yml` is the production-oriented package and exposes the service through the `edge` reverse proxy.
- Full startup, environment variables, cleanup policy, and logging expectations are documented in [runtime.md](./runtime.md).
