# `v1.0.0` Release Checklist

## Status

- Lifecycle: active
- Source of truth: repository
- Mirror: none required yet
- Maturity: active release gate for the `v1.0.0` service-hardening track

## Related Documents

- `Documentation/backlog.md`
- `Documentation/runtime.md`
- `Documentation/operations.md`
- `Documentation/runbook.md`
- `Documentation/archive/v1.0.0-release.md`

## Purpose
This checklist is the controlled release gate for moving the validated `v1.0.0` service state from `develop` to `main`.

## Release Decision
- Merge to `main` only when every required gate below is satisfied.
- If any required gate is incomplete or ambiguous, the answer is `no-go`.
- If a blocker is accepted intentionally, record that acceptance in `backlog.md` before merging.

## Required Evidence
- `TASK-042` browser QA report or equivalent note from a live Docker run.
- `TASK-044` through `TASK-046` negative-path QA notes with observed behavior and risks.
- `TASK-049` integration test coverage for release-sensitive persistence and API flows.
- `TASK-064` browser E2E and CI coverage for the operator happy path.
- `TASK-065` runbook coverage for startup, backup, restore, upgrade, and operator recovery.
- `TASK-067` backup, restore, and rollback-drill evidence from 2026-04-09.
- A current green `./gradlew test` result.
- A current `docker compose` validation note if runtime behavior changed.

## Required Gates
- `TASK-039` upload policy is documented in `runtime.md`.
- `TASK-040` multipart limits and stable upload error handling are implemented and tested.
- `TASK-041` upload constraints are visible in the UI.
- `TASK-042` browser happy-path QA is complete.
- `TASK-043` any high-signal browser QA defects are fixed or explicitly accepted.
- `TASK-044` negative-path URL ingest failure behavior is understood and no release-blocking stuck-job bug remains open.
- `TASK-045` export and artifact failure behavior is understood.
- `TASK-046` worker restart behavior is understood.
- `TASK-047` failure visibility is practical enough for operators to diagnose issues.
- `TASK-048` runtime logs are traceable enough to follow one job end-to-end.
- `TASK-049` release-sensitive persistence and API paths have focused integration coverage.
- `TASK-050` Docker runtime and image choices are documented as MVP compromises.
- `TASK-064` browser E2E is present in source and wired into CI.
- `TASK-065` operator runbook matches the actual runtime shape.
- `TASK-067` backup, restore, and release-recovery procedures are exercised successfully.

## Current Blockers
- No current blockers remain from local evidence for the required gates listed above.
- `TASK-053` executed after the MVP gates were satisfied, with a documented residual QA note that timeout and unsupported-source variants were not separately re-run in `TASK-044`.
- The post-drill analyze-claim defect found during `TASK-067` was fixed before release by deferring queued `ANALYZE` claims until the referenced source video exists under `APP_STORAGE_LOCAL_ROOT`.

## Documentation Gates
- `backlog.md` reflects the current branch state and known limitations.
- `runtime.md` remains the source of truth for upload policy and local runtime expectations.
- Release notes mention the known limits and any accepted follow-up items.

## Known Limitations
- The service is single-tenant and uses one configured operator credential set.
- Zero-downtime upgrades are not supported in `v1.0.0`.
- Source videos under `APP_STORAGE_LOCAL_ROOT` are not backed up by default.
- Worker cold start still depends on external model download when `HF_TOKEN` is not configured.
- Automatic retry, backoff, and dead-letter semantics remain post-release work.

## Rollback / Recovery
- If release validation fails, keep `main` unchanged and continue fixing on `develop`.
- Local runtime state can be reset with `docker compose down -v`.
- Disposable artifact and storage volumes should be treated as recoverable runtime data, not source-controlled state.

## Current Status
As of 2026-04-09 this checklist is satisfied for the controlled `v1.0.0` release. The release decision is supported by the 2026-04-09 `TASK-067` PASS evidence set, the runbook-alignment fixes recorded in this cut, and a current green `./gradlew test` result.
