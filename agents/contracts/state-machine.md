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
- FAILED

## Rules
- State transitions must be explicit.
- Invalid transitions must be rejected.
- Failures move the job to FAILED with an error message.
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
