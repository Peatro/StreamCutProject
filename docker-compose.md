# Local Compose

## Run
```bash
docker compose up --build
```

## Services
- `backend` on `http://localhost:8080`
- `postgres` on `localhost:5432`
- `worker` runs the Python worker entrypoint

## Environment
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/streamcut`
- `SPRING_DATASOURCE_USERNAME=streamcut`
- `SPRING_DATASOURCE_PASSWORD=streamcut`
- `APP_STORAGE_LOCAL_ROOT=/data/storage`

## Shared Volume
- `streamcut-data` is mounted into both backend and worker at `/data`
- backend uses it for local storage
- worker uses it for future media/artifact handling
