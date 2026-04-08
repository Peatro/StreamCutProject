# Job State Machine

## States
- NEW
- QUEUED
- DOWNLOADING
- EXTRACTING_AUDIO
- TRANSCRIBING
- DETECTING_SILENCE
- ANALYZING_WINDOWS
- GENERATING_CANDIDATES
- READY_FOR_REVIEW
- EXPORTING_CLIP
- COMPLETED
- CANCELED
- FAILED

## Rules
- State transitions must be explicit.
- Invalid transitions must be rejected.
- Failures move the job to FAILED with an error message.
- Operator cancellation moves the job to CANCELED.
- Export is a separate step after review.

## Typical Flow
NEW
-> QUEUED
-> DOWNLOADING
-> EXTRACTING_AUDIO
-> TRANSCRIBING
-> DETECTING_SILENCE
-> ANALYZING_WINDOWS
-> GENERATING_CANDIDATES
-> READY_FOR_REVIEW
-> EXPORTING_CLIP
-> COMPLETED
