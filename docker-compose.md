# Local Compose

## Run
```bash
docker compose up --build
```

## Production Package
```bash
docker compose -f docker-compose.production.yml --env-file env.production up --build -d
```

## Services
- `backend` on `http://localhost:8080`
- `postgres` on `localhost:5432`
- `worker` runs the Python worker entrypoint

## Runtime Notes
- `docker-compose.yml` is the explicit local-runtime contract and now starts the backend with `SPRING_PROFILES_ACTIVE=local`.
- `docker-compose.production.yml` is the production-oriented package and exposes the service through the `edge` reverse proxy.
- Full startup, environment variables, cleanup policy, and logging expectations are documented in [runtime.md](./runtime.md).
