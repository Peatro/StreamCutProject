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
Local-first filesystem storage for media assets and exported clips.

## Configuration
- `app.storage.local-root`

## Default
- `data/storage`

## Layout
- `jobs/{jobId}/source` for source video files
- `jobs/{jobId}/audio` for extracted audio
- `jobs/{jobId}/exports` for exported clips

## Notes
- Paths are resolved deterministically from job and candidate identifiers.
- The storage layer is intentionally isolated behind `StorageService`.
