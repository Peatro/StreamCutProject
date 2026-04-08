# API Contracts

## Jobs
### POST /api/jobs/url
Creates a job from a URL.

Request:
```json
{
  "url": "string"
}
```

Response:

```json
{
  "id": "string",
  "status": "NEW"
}
```

### POST /api/jobs/upload

Creates a job from an uploaded file.
The MVP upload policy for file size, request size, supported formats, and oversize behavior is defined in `runtime.md`.
Oversize uploads should return `413 Payload Too Large` with a JSON error body that includes a user-facing `message`.

### GET /api/jobs

Returns a list of jobs.

### GET /api/jobs/{id}

Returns job details.

Current detail responses also expose worker runtime fields such as:
- `processingVersion`
- `currentWorkerId`
- `lastWorkerHeartbeatAt`
- `progressPercent`
- `progressMessage`

### POST /api/jobs/{id}/cancel

Cancels the current queued or in-flight worker run.

### POST /api/jobs/{id}/restart

Invalidates the current worker lease and starts a fresh worker attempt.

## Transcript

### GET /api/jobs/{id}/transcript

Returns transcript segments for the job.

## Candidates

### GET /api/jobs/{id}/candidates

Returns generated clip candidates.

### POST /api/candidates/{id}/approve

Approves a candidate.

### POST /api/candidates/{id}/reject

Rejects a candidate.

## Export

### POST /api/candidates/{id}/export

Starts clip export.

### GET /api/exports/{id}

Returns export status and artifact reference.

## Internal Worker Transport

### POST /api/internal/worker/claims/next

Claims the next queued job for one worker.

Request:
```json
{
  "workerId": "string"
}
```

Response `200`:
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

Response `204`:
No queued job is currently available.

### POST /api/internal/worker/results

Accepts one successful worker processing result.

Request:
```json
{
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "durationSec": 0,
  "language": "string",
  "videoPath": "string or null",
  "audioPath": "string or null",
  "transcriptSegments": [],
  "silenceSegments": [],
  "analysisWindows": [],
  "clipCandidates": []
}
```

Response:
```json
{
  "jobId": 0,
  "status": "READY_FOR_REVIEW"
}
```

### POST /api/internal/worker/progress

Accepts one worker progress heartbeat and stage update.

Request:
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

Response:
```json
{
  "jobId": 0,
  "status": "TRANSCRIBING"
}
```

### POST /api/internal/worker/exports/results

Accepts one successful worker export completion payload.

Request:
```json
{
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "candidateId": 0,
  "artifactPath": "string"
}
```

Response:
```json
{
  "jobId": 0,
  "status": "COMPLETED"
}
```

### POST /api/internal/worker/failures

Accepts one worker processing failure report.

Request:
```json
{
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "failedState": "DOWNLOADING",
  "message": "string"
}
```

Response:
```json
{
  "jobId": 0,
  "status": "FAILED"
}
```
