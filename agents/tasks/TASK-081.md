# TASK-081 Use TwitchDownloaderCLI For Twitch Sources To Fix A/V Desync

## Agent
worker-agent

## Summary
Download Twitch VOD sources with TwitchDownloaderCLI instead of yt-dlp so that ad/muted-segment HLS discontinuities no longer drift audio out of sync; keep yt-dlp for all non-Twitch sources.

## Context
On a real 3h10m Twitch VOD, exported clips had audio progressively out of sync with video (synced near the start, ~7.5s off at ~2h in). Diagnosed: the drift is in the downloaded SOURCE, caused by yt-dlp's handling of Twitch HLS discontinuities (ad / muted DMCA segments) — desync accumulates across each discontinuity. Confirmed: a short isolated section download is synced, but the same timestamp in the full download is desynced; the audio track ends 12-22s shorter than video. Per-clip export fixes, audio resampling, and full CFR re-encode were all rejected (drift is in source; re-encode is ~50min/job and shifts candidate timestamps). TwitchDownloaderCLI correctly handles these discontinuities and produces a synced file; binary v1.56.4 is a self-contained 71MB Linux x64 ELF (no .NET runtime) and was confirmed running inside the download-worker container.

## Problem Frame
- Symptom: clips from Twitch VODs have progressively desynced audio.
- Suspected Layer: source download (`worker/src/streamcut_worker/services/source_materializer.py`) + worker image (`worker/Dockerfile`).
- Touched Contracts: none (same `PlatformVideoDownloader` protocol and return type; a different tool fulfills it for Twitch).
- Done Criterion: a full Twitch VOD download is A/V-synced end to end; non-Twitch sources still use yt-dlp.

## Scope
- **Worker image:** in `worker/Dockerfile` (the CPU image used by `download-worker`), install TwitchDownloaderCLI v1.56.4 Linux x64 (download the release zip from GitHub, extract the `TwitchDownloaderCLI` binary to a PATH location, `chmod +x`). Pin the version. ffmpeg is already present in the image (used by silence/export) — TwitchDownloaderCLI uses it for muxing; point it at the system ffmpeg if needed (`--ffmpeg-path`).
- **Routing:** in `source_materializer.py`, route Twitch URLs to a new `TwitchDownloaderCliDownloader` (implementing the existing `PlatformVideoDownloader` protocol) while YouTube/Vimeo/other platform URLs continue to use `YtDlpPlatformDownloader`. Keep the existing direct-media and durable-reference paths unchanged.
  - Extract the Twitch VOD id from the URL (e.g. `https://www.twitch.tv/videos/<id>`).
  - Build the download command: `TwitchDownloaderCLI videodownload --id <id> -q 1080p60 -o <target> --temp-path <tmp>` (prefer the source/1080p60 rendition; choose a sensible fallback if 1080p60 is unavailable). Honor the same `<= 1080p` intent as the prior yt-dlp format.
  - Wire `on_progress`: parse TwitchDownloaderCLI's progress output and call `on_progress` so the worker keeps heartbeating, applying the same "only heartbeat on real advance" spirit as TASK-077 (gate on the reported percentage increasing).
  - Resolve and return the produced media file the same way the yt-dlp path does (the existing candidate-file selection logic can be reused or mirrored).
- Preserve error wrapping: failures must raise `SourceMaterializationError` with `failed_state="DOWNLOADING"`, like the yt-dlp path.

## Out of Scope
- No change to non-Twitch download behavior (yt-dlp stays for YouTube/Vimeo/direct/durable).
- No backend/DB/contract changes; no transport changes.
- No post-download re-encode/normalization (the point of this task is that the download itself is synced).
- No change to the analysis/export/loudness code.

## Inputs
- `worker/src/streamcut_worker/services/source_materializer.py` (`PlatformVideoDownloader`, `YtDlpPlatformDownloader`, `_requires_platform_downloader`, `_download_with_platform_extractor`)
- `worker/Dockerfile` (CPU worker image; confirm `download-worker` builds from it)
- `worker/tests/test_source_materializer.py`
- TwitchDownloaderCLI: https://github.com/lay295/TwitchDownloader/releases/download/1.56.4/TwitchDownloaderCLI-1.56.4-Linux-x64.zip (videodownload subcommand: `--id`, `-b/--beginning`, `-e/--ending`, `-q`, `-o`, `--temp-path`, `--ffmpeg-path`)

## Touched Contracts
- none

## Schema Impact
- none

## Operational Risk
- medium — new build-time dependency in the worker image and a new download path for Twitch. Non-Twitch paths unchanged. Reverting restores the yt-dlp Twitch path.

## Rollback Or Migration Note
- none. Existing already-downloaded (desynced) sources are not migrated; re-running a Twitch job produces a synced source.

## Expected Deliverables
- code
- tests

## Constraints
- keep yt-dlp for non-Twitch; do not remove it
- pin the TwitchDownloaderCLI version (1.56.4); do not fetch "latest" at build time
- match the existing `PlatformVideoDownloader` protocol and `SourceMaterializationError` behavior exactly
- the new downloader must report progress so heartbeats continue (reuse the TASK-077 progress-gating intent)
- no unrelated refactor

## Acceptance Criteria
- Twitch URLs are routed to TwitchDownloaderCLI; YouTube/Vimeo/other still use yt-dlp (unit test on routing selection)
- Twitch VOD id is correctly parsed from `twitch.tv/videos/<id>` URLs (unit test)
- the TwitchDownloaderCLI command is constructed with the expected id/quality/output args (unit test on command build)
- download failures surface as `SourceMaterializationError(failed_state="DOWNLOADING")`
- `worker/Dockerfile` installs the pinned TwitchDownloaderCLI binary on PATH
- existing yt-dlp tests and non-Twitch behavior remain green

## Notes
- Empirically validated before integration: a TwitchDownloaderCLI download of this VOD produces an A/V-synced source where yt-dlp did not.
- After merge: rebuild download-worker, re-run a Twitch job, and confirm an exported clip from ~2h into the VOD is in sync (the real end-to-end check).
