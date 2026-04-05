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

### GET /api/jobs

Returns a list of jobs.

### GET /api/jobs/{id}

Returns job details.

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
