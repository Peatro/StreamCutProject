# Storage Configuration

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
