# Local Compose

## Run
```bash
docker compose up --build
```

## Services
- `backend` on `http://localhost:8080`
- `postgres` on `localhost:5432`
- `worker` runs the Python worker entrypoint

## Runtime Notes
- `docker-compose.yml` is the explicit local-runtime contract and now starts the backend with `SPRING_PROFILES_ACTIVE=local`.
- Full startup, environment variables, cleanup policy, and logging expectations are documented in [runtime.md](./runtime.md).
