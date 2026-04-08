# Operations Guide

## Status

- Lifecycle: active
- Source of truth: repository
- Mirror: `Operations Guide.md`
- Maturity: initial operational skeleton; should deepen with `TASK-059` through `TASK-063`

## Purpose

This document captures the operator-facing operational model for the service.

It is not the release gate and not the full runtime contract.

Use it to answer:

- what operators should monitor
- what failure classes are expected
- what routine operational actions exist
- which documents own deeper detail

---

## Scope

This guide is intentionally lightweight.

It should summarize:

- runtime surfaces
- diagnostics surfaces
- recovery surfaces
- cleanup expectations

Deeper detail should remain in:

- `Documentation/runtime.md`
- `Documentation/release-checklist.md`
- `Documentation/backlog.md`
- future ADRs where architectural decisions matter

---

## Current Runtime Shape

Current service shape:

- one backend
- PostgreSQL
- S3-compatible artifact storage
- one or more workers
- current split between `download-worker` and `processing-worker`

The service is currently on a path toward:

- explicit task lifecycle
- better diagnostics
- clearer operator controls

---

## Operator Concerns

Operators should be able to answer:

- is the backend healthy and ready
- are workers alive and claiming work
- is queue or task backlog growing
- are jobs stuck or repeatedly recovering
- are artifacts being retained and cleaned up as expected

---

## Operational Surfaces

### Health

Track:

- backend health
- backend readiness
- worker recent activity

See also:

- `TASK-059`

### Recovery

Track:

- stale task recovery
- lease expiry behavior
- operator-visible requeue/retry/cancel actions

See also:

- `TASK-060`
- `TASK-061`

### Observability

Track:

- backlog by task type
- failure rate
- stale executions
- export activity

See also:

- `TASK-062`

### Retention

Track:

- source retention
- export retention
- temporary local storage growth

Current policy:

- source video files for `COMPLETED` and `FAILED` jobs are eligible for cleanup 7 days after the job reached its terminal state
- exported clip artifacts for `COMPLETED` jobs are eligible for cleanup 30 days after completion
- jobs in `NEW`, `QUEUED_FOR_DOWNLOAD`, `DOWNLOADING`, `QUEUED_FOR_PROCESSING`, `EXTRACTING_AUDIO`, `TRANSCRIBING`, `DETECTING_SILENCE`, `ANALYZING_WINDOWS`, `GENERATING_CANDIDATES`, `READY_FOR_REVIEW`, and `EXPORTING_CLIP` are never retention-cleaned
- `finishedAt` is used as the terminal-state timestamp; `updatedAt` is used as a fallback for older terminal rows without `finishedAt`
- when a retained file is already missing, cleanup logs it as non-fatal and clears the stored reference so the item is not retried forever
- expired successful exports stop being downloadable after cleanup because the stored artifact reference is removed

Configuration:

- `app.retention.source-retention`: source retention window, default `7d`
- `app.retention.artifact-retention`: artifact retention window, default `30d`
- `app.retention.cleanup-cron`: cleanup schedule, default `0 0 3 * * *`
- `app.retention.cleanup-zone`: cron timezone, default `UTC`

Operational notes:

- the scheduler logs one summary line per run with source and artifact cleanup counts
- each deleted or already-missing item is logged with `jobId`; artifact cleanup logs also include `candidateId`
- operators should expect downloads to keep working for successful exports until the artifact retention window expires

See also:

- `TASK-063`

---

## Routine Documentation Duties

When runtime behavior changes, update:

- `Documentation/runtime.md` for runtime contract changes
- `Documentation/backlog.md` for task status and sequencing
- `Documentation/operations.md` for operator-facing meaning

---

## Near-Term Gaps

This guide should expand as the service matures.

Near-term missing operational maturity:

- richer diagnostics
- explicit operator recovery controls
- richer metrics surface
- richer retention reporting beyond scheduler logs

---

## Related Documents

- `Documentation/runtime.md`
- `Documentation/backlog.md`
- `Documentation/release-checklist.md`
- `Documentation/architecture-roadmap.md`
- `Documentation/documentarian-role.md`
