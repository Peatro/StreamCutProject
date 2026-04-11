# Browser E2E Tests

Start the backend first with the `local` profile so the authenticated UI and `/api/internal/e2e/**` fixture endpoints are available.
Run the browser suite against the backend only. Do not start the download or processing workers, otherwise queued fixtures can be claimed before the page assertions run.

Run the suite in headless Chrome:

```bash
./gradlew e2eTest
```

Run the suite in a visible browser for debugging:

```bash
./gradlew e2eTest -Dselenide.headless=false
```

Override the backend URL or operator credentials when needed:

```bash
./gradlew e2eTest -Dselenide.baseUrl=http://localhost:8080 -De2e.username=operator -De2e.password=operator-password
```
