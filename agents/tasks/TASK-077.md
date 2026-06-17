# TASK-077 Make Download Stall Detectable By Gating Heartbeat On Real Progress

## Agent
worker-agent

## Summary
Make a stalled download trigger the existing automatic retry/recovery by only emitting a worker heartbeat when the download actually advances, instead of on every yt-dlp progress tick.

## Context
The backend already has full automatic recovery: when a worker task goes stale (heartbeat older than the per-task stale timeout), `VodJobService.recoverStaleExecutions` fails the execution, applies the retry/backoff policy, and re-queues the task (TASK-068/069). The action exists; the trigger is the gap.

Staleness is keyed on `lastHeartbeatAt`, which the backend refreshes on every worker progress callback (`VodJobService` ~line 765, unconditional). During download the only heartbeat source is the yt-dlp progress hook in `worker/src/streamcut_worker/services/source_materializer.py` (`_yt_dlp_hook`). When a Twitch download stalls, yt-dlp commonly keeps emitting `downloading` events with a frozen `downloaded_bytes` (retries / keep-alive). Each no-op event still refreshes the heartbeat, so the task never looks stale and never gets recovered — the operator has to restart it by hand.

In short: the heartbeat currently proves "process alive", not "making progress". A live-but-not-advancing download is invisible to recovery.

## Problem Frame
- Symptom: a stalled download hangs indefinitely with no automatic retry; recovery only happens manually.
- Suspected Layer: worker download progress reporting (`source_materializer.py` `_yt_dlp_hook`) — it reports progress (and thus heartbeats) even when no bytes are transferred.
- Touched Contracts: none (worker emits the same progress callback signature, just less often during a stall).
- Done Criterion: a download that transfers zero bytes for the DOWNLOAD stale window stops heartbeating and is picked up by the existing stale-recovery/retry path.

## Scope
- In `worker/src/streamcut_worker/services/source_materializer.py`, gate the download progress callback on actual progress: only invoke `on_progress` when `downloaded_bytes` has strictly increased beyond the maximum seen so far for this download (always allow the first real progress event).
  - Use a monotonic max-seen-bytes guard so brief per-fragment byte fluctuations don't cause false heartbeats; only a genuine increase counts.
  - Frozen `downloaded_bytes` (no increase) must NOT call `on_progress` → no heartbeat → the existing DOWNLOAD stale timeout fires → existing retry/backoff recovers the task.
- Do NOT change the transcription / silence / analyze stages: those use an intentional periodic stage heartbeat (`_start_stage_heartbeat`) because they legitimately run long with sparse progress events. This task is download-only.

## Out of Scope
- No backend/Java change and no DB/schema change. The fix reuses the existing recovery machinery via the heartbeat the worker already sends.
- No new `lastProgressAt` column or new staleness model (heavier alternative, not needed).
- No change to the retry/backoff policy itself (already exists from TASK-068).
- No new dependency.

## Inputs
- `worker/src/streamcut_worker/services/source_materializer.py` (`YtDlpPlatformDownloader._yt_dlp_hook`)
- `worker/src/streamcut_worker/pipeline/job_runner.py` (`_on_yt_dlp_progress`, download progress mapping)
- `src/main/java/.../workerexecution/WorkerExecutionProperties.java` (DOWNLOAD stale timeout default = 15m; reference only — see Notes for the tunable)
- `worker/tests/test_source_materializer.py` (test patterns)

## Touched Contracts
- none

## Schema Impact
- none

## Operational Risk
- low — narrows when the worker reports download progress; legitimately advancing downloads (even slow ones) still heartbeat on every byte increase. Only a true zero-byte stall stops heartbeating.

## Rollback Or Migration Note
- none — reverting restores the prior (ungated) progress reporting.

## Expected Deliverables
- code
- tests

## Constraints
- use existing stack only; no new dependency
- download path only; do not touch stage heartbeats for transcription/silence/analyze
- a slow-but-advancing download must keep heartbeating (any real byte increase counts) — do not introduce a minimum-throughput threshold that could false-positive on slow connections
- keep the progress percentage the operator sees correct when progress does advance

## Acceptance Criteria
- when fed repeated yt-dlp `downloading` events with a non-increasing `downloaded_bytes`, the gated hook does not invoke `on_progress` after the first stall tick (covered by a unit test)
- when fed events with increasing `downloaded_bytes`, `on_progress` is invoked on each increase with a correct percentage (unit test)
- transcription/silence/analyze heartbeat behavior is unchanged
- `worker/tests/test_source_materializer.py` covers the stall (no heartbeat) and advancing (heartbeat) cases

## Notes
- Recovery latency is governed by the DOWNLOAD stale timeout (`app.worker-execution.stale-timeout-overrides.DOWNLOAD`, default 15m). That is a runtime config knob, not part of this code change; if 15m feels too long in practice, lower it via configuration (e.g. compose env) rather than hardcoding — calibration, not code.
- This task only makes stalls *detectable*; the fail → backoff → re-queue behavior is already implemented and is reused as-is.
