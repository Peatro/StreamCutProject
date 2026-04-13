# Storage Configuration

## Status

- Lifecycle: active
- Source of truth: repository
- Mirror: none required yet
- Maturity: active but expected to evolve with `TASK-071` and `TASK-072`

## Related Documents

- `Documentation/runtime.md`
- `Documentation/operations.md`
- `Documentation/architecture-roadmap.md`
- `Documentation/backlog.md`

## Purpose
Define which media artifacts are durable and which remain scratch-only runtime files.

## Configuration
- `app.storage.local-root`
- `app.artifact-storage.*`

## Default
- `data/storage`

## Durable Contract
- `vod_job.source_video_reference` is the durable backend-known reference for the source video.
- `clip_candidate.exported_clip_path` is the durable backend-known reference for one completed export artifact.
- In `S3` mode those references are object-storage keys exposed as `s3://...`.
- In `LOCAL` mode those references degenerate to normalized local paths and should be treated as a development fallback, not a scale-out contract.

## Scratch Layout
- `jobs/{jobId}/source` for local source-video materialization and cache files
- `jobs/{jobId}/audio` for extracted audio scratch files
- `jobs/{jobId}/exports` for local export scratch files before durable persistence

## Execution Rules
- `download-worker` is responsible for materializing an origin URL or uploaded file into local scratch and the backend then persists a durable `source_video_reference`.
- `processing-worker` and `export-worker` may use a local `storage_video_path` when it is already available, but must be able to fall back to the durable source reference via the internal worker download endpoint.
- `storage_video_path` and `storage_audio_path` are runtime scratch details, not the long-term execution contract.
- Extracted audio remains scratch-only because it is cheaper to recreate than to persist durably in the current architecture.
- Completed exports remain durable even after local scratch cleanup.

## Notes
- `StorageService` owns deterministic local scratch paths under `app.storage.local-root`.
- `ArtifactStorageService` owns durable source/export references and signed URL generation.
- Source retention cleanup removes only local scratch source files. Durable source references remain intact in `S3` mode and are cleared only when the reference itself is local-backed.
