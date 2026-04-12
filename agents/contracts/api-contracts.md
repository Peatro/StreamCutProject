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
Returns aggregate job details.

Current detail responses also expose runtime projection fields such as:
- `processingVersion`
- `currentWorkerId`
- `lastWorkerHeartbeatAt`
- `progressPercent`
- `progressMessage`

These are aggregate-facing projections, not a replacement for task/execution inspection models.

### POST /api/jobs/{id}/retry
Retries a failed job by moving it back to `QUEUED_FOR_DOWNLOAD`.

Effects:
- only valid when the aggregate job status is `FAILED`
- increments `processingVersion` to invalidate stale worker callbacks
- queues a fresh `DOWNLOAD` task
- records a `JOB_RETRIED` event

### POST /api/jobs/{id}/cancel
Cancels a queued job before worker execution begins.

Effects:
- only valid when the aggregate job status is `QUEUED_FOR_DOWNLOAD` or `QUEUED_FOR_PROCESSING`
- moves the aggregate job to `CANCELED`
- records a `JOB_CANCELED` event

### POST /api/jobs/{id}/force-fail
Force-fails an active worker run that appears stuck.

Effects:
- only valid when the aggregate job status is `DOWNLOADING`, `EXTRACTING_AUDIO`, `TRANSCRIBING`, `DETECTING_SILENCE`, `ANALYZING_WINDOWS`, `GENERATING_CANDIDATES`, or `EXPORTING_CLIP`
- moves the aggregate job to `FAILED`
- records a `JOB_FORCE_FAILED` event

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
Claims the next compatible queued task for one worker.

Request:
```json
{
  "workerId": "string",
  "workerRole": "DOWNLOAD_OR_PROCESSING_OR_EXPORT",
  "whisperDevice": "cpu_or_cuda_or_null"
}
```

- `whisperDevice` is optional and is expected only from processing workers.

Response `200`:
```json
{
  "executionId": 0,
  "jobId": 0,
  "processingVersion": 1,
  "taskType": "DOWNLOAD_OR_ANALYZE_OR_EXPORT",
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
No compatible queued task is currently available.

### POST /api/internal/worker/downloads/results
Accepts one successful download task result.

Request:
```json
{
  "executionId": 0,
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "videoPath": "string"
}
```

Response:
```json
{
  "jobId": 0,
  "status": "QUEUED_FOR_PROCESSING"
}
```

### POST /api/internal/worker/results
Accepts one successful analysis task result.

Request:
```json
{
  "executionId": 0,
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
Accepts one worker progress heartbeat and stage update for the active execution.

Request:
```json
{
  "executionId": 0,
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
Accepts one successful export task completion payload.

Request:
```json
{
  "executionId": 0,
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
Accepts one worker task failure report.

Request:
```json
{
  "executionId": 0,
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
