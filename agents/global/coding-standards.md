# Coding Standards

## General
- Prefer explicit code over clever code.
- Use consistent naming.
- Avoid deep inheritance.
- Prefer composition over unnecessary abstraction.

## Java Standards
- Use Java 21 features responsibly.
- Controllers should be thin.
- Services contain application logic.
- Repositories only handle persistence.
- DTOs must not expose entities directly.
- Validate inputs explicitly.
- Use enums for bounded statuses and types.
- Use MapStruct where DTO mapping is repetitive and stable.

## Python Standards
- Keep worker logic modular and scriptable.
- Use typed models where practical.
- Separate pipeline steps into isolated services/functions.
- Keep shell/ffmpeg integration encapsulated.
- Return structured JSON-compatible results.

## SQL / DB
- Schema changes must use migrations.
- Avoid destructive schema changes without explicit approval.
- Names should be stable and descriptive.

## Frontend
- Keep UI minimal and functional.
- Avoid heavy abstractions in MVP.
- Prefer clarity over fancy architecture.

## Logging
- Log state transitions and failures clearly.
- Do not log sensitive data.
- Log enough context to debug failed jobs.
