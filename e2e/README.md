# Browser E2E

This suite covers operator browser flows that are stable against backend + Postgres only. It does not wait for the worker, Whisper downloads, or transcription completion.

## Install

```bash
pip install -r e2e/requirements.txt
python -m playwright install chromium
```

## Run Locally

Start Postgres first:

```bash
docker compose -f docker-compose.ci.yml up -d
```

Start the backend with the `local` profile in a separate shell:

```bash
SPRING_PROFILES_ACTIVE=local \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/streamcut \
SPRING_DATASOURCE_USERNAME=streamcut \
SPRING_DATASOURCE_PASSWORD=streamcut \
./gradlew bootRun
```

Run the suite:

```bash
pytest e2e/tests/
```

Optional environment overrides:

```bash
STREAMCUT_BASE_URL=http://localhost:8080
STREAMCUT_TEST_USER=operator
STREAMCUT_TEST_PASSWORD=operator-password
```

## Failure Output

Failed tests write artifacts under `e2e/artifacts/`:

- `failure.png`: full-page screenshot at the point of failure
- `page.html`: rendered DOM snapshot
- `url.txt`: final browser URL

Most failures are either:

- auth bootstrap failures, usually from the backend not running with the `local` profile
- selector mismatches after a browser-visible regression
- backend startup or database connectivity failures before the first page load
