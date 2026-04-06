# MVP Release Checklist

## Purpose
This checklist is the release gate for moving the validated MVP from `develop` to `main`.

## Release Decision
- Merge to `main` only when every required gate below is satisfied.
- If any required gate is incomplete or ambiguous, the answer is `no-go`.
- If a blocker is accepted intentionally, record that acceptance in `backlog.md` before merging.

## Required Evidence
- `TASK-042` browser QA report or equivalent note from a live Docker run.
- `TASK-044` through `TASK-046` negative-path QA notes with observed behavior and risks.
- `TASK-049` integration test coverage for release-sensitive persistence and API flows.
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
- `TASK-054` stale artifact semantics after failed export are either fixed or explicitly accepted.
- `TASK-046` worker restart behavior is understood.
- `TASK-047` failure visibility is practical enough for operators to diagnose issues.
- `TASK-048` runtime logs are traceable enough to follow one job end-to-end.
- `TASK-049` release-sensitive persistence and API paths have focused integration coverage.
- `TASK-050` Docker runtime and image choices are documented as MVP compromises.

## Current Blockers
- `TASK-042` through `TASK-049` are not all complete yet.
- Browser QA evidence from `TASK-042` and any follow-up fixes from `TASK-043` still do not exist.
- `TASK-054` is still open, so stale-artifact behavior is not yet dispositioned for release.
- `TASK-053` must not proceed until the required gates and evidence above exist.

## Documentation Gates
- `backlog.md` reflects the current branch state and known limitations.
- `runtime.md` remains the source of truth for upload policy and local runtime expectations.
- Release notes mention the known limits and any accepted follow-up items.

## Known Limitations
- Local Docker remains the MVP runtime, not the final deployment target.
- Backend and worker image choices are acceptable for the MVP but are not a production recommendation.
- Worker cold start still depends on external model download when `HF_TOKEN` is not configured.
- Any unresolved URL ingest failure that leaves jobs stuck in `DOWNLOADING` blocks release.

## Rollback / Recovery
- If release validation fails, keep `main` unchanged and continue fixing on `develop`.
- Local runtime state can be reset with `docker compose down -v`.
- Disposable artifact and storage volumes should be treated as recoverable runtime data, not source-controlled state.

## Current Status
As of 2026-04-06 this checklist is not yet satisfied because browser QA is still incomplete and `TASK-054` has not yet closed the stale-artifact release blocker.
