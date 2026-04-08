# Worker Protocol

## Purpose
Defines the backend <-> worker transport and payload contract for the MVP.

## Transport Model
- MVP transport is HTTP polling plus HTTP callback.
- The worker polls the backend for one queued job at a time.
- The backend returns one claim payload or `204 No Content` when no job is available.
- The worker reports either a success payload or a failure payload back to the backend after processing.
- Worker-facing endpoints are internal transport endpoints and must stay separate from UI-facing APIs.

## Flow
1. Worker sends a claim request.
2. Backend atomically claims the next queued job if one exists.
3. Backend returns the worker input payload for that job.
4. Worker processes the job against the shared storage volume.
5. Worker submits either a success result payload or a failure payload.
6. Backend persists results or failure state and returns an acknowledgement payload.

## Claim Request
`POST /api/internal/worker/claims/next`

```json
{
  "workerId": "string"
}
```

## Claim Response Payload
```json
{
  "jobId": 0,
  "processingVersion": 1,
  "taskType": "ANALYZE_OR_EXPORT",
  "videoPath": "string or null",
  "sourceType": "URL_OR_FILE",
  "sourceUrl": "string or null",
  "candidateId": "number or null",
  "clipStartSec": "number or null",
  "clipEndSec": "number or null",
  "artifactPath": "string or null"
}
```

If no queued job is available, the backend returns `204 No Content`.

## Success Result Payload

```json
{
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "durationSec": 0,
  "language": "string",
  "videoPath": "string or null",
  "audioPath": "string or null",
  "transcriptSegments": [
    {
      "startSec": 0.0,
      "endSec": 0.0,
      "text": "string",
      "wordCount": 0
    }
  ],
  "silenceSegments": [
    {
      "startSec": 0.0,
      "endSec": 0.0,
      "durationSec": 0.0
    }
  ],
  "analysisWindows": [
    {
      "startSec": 0.0,
      "endSec": 0.0,
      "speechDensity": 0.0,
      "silenceRatio": 0.0,
      "emotionHits": 0,
      "continuityScore": 0.0,
      "totalScore": 0.0
    }
  ],
  "clipCandidates": [
    {
      "startSec": 0.0,
      "endSec": 0.0,
      "score": 0.0,
      "transcriptExcerpt": "string"
    }
  ]
}
```

## Progress Update Payload
`POST /api/internal/worker/progress`

```json
{
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "status": "TRANSCRIBING",
  "progressPercent": 48,
  "message": "string"
}
```

## Export Result Payload
`POST /api/internal/worker/exports/results`

```json
{
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "candidateId": 0,
  "artifactPath": "string"
}
```

## Failure Result Payload
`POST /api/internal/worker/failures`

```json
{
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "failedState": "DOWNLOADING",
  "message": "string"
}
```

## Acknowledgement Payload
Returned by both success and failure callbacks.

```json
{
  "jobId": 0,
  "status": "READY_FOR_REVIEW_OR_FAILED"
}
```

## Contract Rules

- Field names must remain stable.
- Output must be valid JSON-compatible data.
- Missing optional values must be explicit.
- Schema changes require explicit task approval.
- `jobId` is a numeric identifier in JSON.
- Empty collections must be returned as empty arrays, not `null`.
- Paths must reference artifacts visible to both backend and worker through the shared storage root.
- `processingVersion` must be echoed back unchanged from the claim payload so stale callbacks can be rejected safely.
- `taskType` must be `ANALYZE` or `EXPORT`.
- `videoPath` is required for `FILE` jobs and may be `null` for `URL` jobs before worker-side download.
- `candidateId`, `clipStartSec`, `clipEndSec`, and `artifactPath` are required for `EXPORT` jobs and must be `null` for normal analysis jobs.
- Analysis success payloads should return resolved `videoPath` and `audioPath` when those artifacts are known, so backend export flow can reuse them later.
- `failedState` must reference the explicit processing state where the job failed.
