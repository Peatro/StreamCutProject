# State Machines

## Purpose
This project uses multiple state machines.
Do not collapse user-facing status, queue status, execution status, and clip moderation/export status into one flat lifecycle.

## VodJobStatus
`vod_job.status` is the aggregate projection visible to users and operators.

Current states:
- NEW
- QUEUED_FOR_DOWNLOAD
- DOWNLOADING
- QUEUED_FOR_PROCESSING
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

Rules:
- transitions must be explicit
- aggregate status is derived from accepted task/execution progress
- operator retry from `FAILED` moves the aggregate back to `QUEUED_FOR_DOWNLOAD` and increments `processing_version`
- operator cancel is only valid from `QUEUED_FOR_DOWNLOAD` or `QUEUED_FOR_PROCESSING` and ends in `CANCELED`
- operator force-fail is only valid from active worker states and ends in `FAILED`
- export is user-triggered after moderation, not an automatic continuation of analysis

Typical aggregate flow:
NEW
-> QUEUED_FOR_DOWNLOAD
-> DOWNLOADING
-> QUEUED_FOR_PROCESSING
-> EXTRACTING_AUDIO
-> TRANSCRIBING
-> DETECTING_SILENCE
-> ANALYZING_WINDOWS
-> GENERATING_CANDIDATES
-> READY_FOR_REVIEW
-> EXPORTING_CLIP
-> COMPLETED

## WorkerTaskStatus
`worker_task` is the queue/runtime work unit.

States:
- QUEUED
- CLAIMED
- RUNNING
- SUCCEEDED
- FAILED
- CANCELED
- DEAD_LETTERED

Rules:
- only one active execution should own a claimed task at a time
- `CLAIMED` means lease acquired but not yet advanced to active processing
- `RUNNING` means progress/heartbeat has been observed for the active execution
- terminal states are `SUCCEEDED`, `FAILED`, `CANCELED`, and `DEAD_LETTERED`
- retryable failures may move a task back to `QUEUED` with a delayed `available_at`
- recovery may move a stale task back to `QUEUED` if the active lease is no longer valid and the retry budget is not exhausted
- exhausted tasks must enter `DEAD_LETTERED` instead of silently looping

Typical flow:
QUEUED
-> CLAIMED
-> RUNNING
-> SUCCEEDED

Failure branches:
- CLAIMED -> QUEUED
- RUNNING -> QUEUED
- CLAIMED -> FAILED
- RUNNING -> FAILED
- CLAIMED -> CANCELED
- RUNNING -> CANCELED
- CLAIMED -> DEAD_LETTERED
- RUNNING -> DEAD_LETTERED
- QUEUED -> DEAD_LETTERED

## WorkerExecutionStatus
`worker_execution` tracks one concrete attempt/lease instance for a task.

States:
- CLAIMED
- RUNNING
- SUCCEEDED
- FAILED
- CANCELED

Rules:
- each callback must reference the owning `executionId`
- execution status must never silently rewrite task ownership
- stale or invalid callbacks must be rejected when `processing_version` or execution ownership no longer matches
- execution history is append-only from an operational perspective, even when aggregate state is projected elsewhere

Typical flow:
CLAIMED
-> RUNNING
-> SUCCEEDED

Failure branches:
- CLAIMED -> FAILED
- RUNNING -> FAILED
- CLAIMED -> CANCELED
- RUNNING -> CANCELED

## Clip Lifecycle
Clip state is split between moderation and export.

Moderation states:
- PENDING
- APPROVED
- REJECTED

Export states:
- NOT_REQUESTED
- IN_PROGRESS
- COMPLETED
- FAILED

Rules:
- moderation and export are separate concerns
- only approved candidates should enter export
- export failure does not invalidate the candidate itself
- export completion should surface an artifact reference, not require raw media streaming through backend business logic
