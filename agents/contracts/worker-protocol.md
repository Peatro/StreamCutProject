# Worker Protocol

## Purpose
Defines the backend <-> worker payload contract.

## Input Payload
```json
{
  "jobId": "string",
  "videoPath": "string",
  "sourceType": "URL_OR_FILE",
  "sourceUrl": "string or null"
}
```

## Output Payload

```json
{
  "jobId": "string",
  "durationSec": 0,
  "language": "string",
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

## Contract Rules

- Field names must remain stable.
- Output must be valid JSON-compatible data.
- Missing optional values must be explicit.
- Schema changes require explicit task approval.
