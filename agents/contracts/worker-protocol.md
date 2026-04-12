# Worker Protocol

## Purpose
Defines the backend <-> worker transport and payload contract for the current task-centric MVP.

## Transport Model
- transport is HTTP polling plus HTTP callback
- the worker polls the backend for one task at a time
- the current endpoint name `claims/next` is legacy naming; the operational contract is task-centric
- the backend returns one claimed task payload or `204 No Content` when no task is available
- the worker reports progress, success, or failure against the active execution
- worker-facing endpoints are internal transport endpoints and must stay separate from UI-facing APIs

## Core Identity Rules
- `jobId` identifies the user-facing aggregate context
- `executionId` identifies the active worker execution and is required for every callback
- `processingVersion` is the lease/version guard and must be echoed back unchanged
- `taskType` identifies the concrete work unit: `DOWNLOAD`, `ANALYZE`, or `EXPORT`
- backend remains the source of truth for orchestration, ownership, and acceptance of callbacks
- backend may defer claims for retrying tasks until their `availableAt` time; workers never schedule retries themselves
- backend may return a terminal dead-lettered task state when the retry budget is exhausted; payload shapes stay unchanged

## Flow
1. Worker sends a claim request with `workerId`, `workerRole`, and optional `whisperDevice`.
2. Backend atomically claims the next compatible task if one exists.
3. Backend returns one dispatch payload tied to the new `executionId`.
4. Worker executes exactly that task.
5. Worker sends progress, success, or failure callbacks for the same `executionId`.
6. Backend accepts or rejects callbacks based on execution ownership and `processingVersion`.

## Claim Request
`POST /api/internal/worker/claims/next`

```json
{
  "workerId": "string",
  "workerRole": "DOWNLOAD_OR_PROCESSING",
  "whisperDevice": "cpu_or_cuda_or_null"
}
```

- `whisperDevice` is optional.
- processing workers may send it to report the transcription device used for the claimed execution.
- download workers should omit it.

## Claim Response Payload
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

If no compatible queued task is available, the backend returns `204 No Content`.

## Download Result Payload
`POST /api/internal/worker/downloads/results`

```json
{
  "executionId": 0,
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "videoPath": "string"
}
```

## Analysis Result Payload
`POST /api/internal/worker/results`

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
  "executionId": 0,
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "status": "TASK_OR_JOB_STAGE",
  "progressPercent": 48,
  "message": "string"
}
```

## Export Result Payload
`POST /api/internal/worker/exports/results`

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

## Failure Result Payload
`POST /api/internal/worker/failures`

```json
{
  "executionId": 0,
  "jobId": 0,
  "workerId": "string",
  "processingVersion": 1,
  "failedState": "TASK_OR_JOB_STAGE",
  "message": "string"
}
```

## Acknowledgement Payload
Returned by worker callback endpoints.

```json
{
  "jobId": 0,
  "status": "READY_FOR_REVIEW_OR_FAILED_OR_COMPLETED"
}
```

## Contract Rules
- field names must remain stable
- output must be valid JSON-compatible data
- missing optional values must be explicit
- schema changes require explicit task approval
- `jobId` remains the aggregate identifier in worker payloads
- `executionId` must be echoed back unchanged from the claim payload
- `processingVersion` must be echoed back unchanged from the claim payload so stale callbacks can be rejected safely
- empty collections must be returned as empty arrays, not `null`
- paths must reference artifacts visible to both backend and worker through the shared storage or object-storage contract
- `taskType` must be `DOWNLOAD`, `ANALYZE`, or `EXPORT`
- `videoPath` may be `null` before source materialization is complete
- `candidateId`, `clipStartSec`, `clipEndSec`, and `artifactPath` are export-specific fields
- worker must execute exactly one claimed task payload at a time
- worker must not invent follow-up tasks; backend owns orchestration and queue transitions
- worker should remain idempotent across retries whenever practical
- delayed availability and retry exhaustion are backend concerns, not transport concerns
