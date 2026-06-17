# TASK-080 Fix Clip Export: Synced Source Download And Fast, Bounded Export

## Agent
worker-agent

## Summary
Fix two confirmed export defects on long Twitch VODs: (1) the downloaded source drifts audio out of sync because a separate audio track is merged onto an already-muxed stream, and (2) exporting a clip decodes the whole source up to the clip and saturates the host CPU.

## Context
Real-use run (3h10m Twitch VOD) surfaced two bugs, both diagnosed and fixed empirically against the live source before this task:

**Desync (source-level):** the downloaded source had audio ~12.2s shorter than video, drifting progressively (~0.107%) — at ~2h in, audio was ~7.5s out of sync, visibly broken. Root cause: the download format `bestvideo[height<=1080]*+bestaudio/best[height<=1080]` (`source_materializer.py:57`) makes yt-dlp merge a separate `Audio_Only` HLS track onto Twitch's already-muxed video, and the two HLS timelines diverge. Twitch serves every video rendition pre-muxed (e.g. `1080p60` already carries `mp4a` audio). Verified fix: downloading a single muxed format gave matching A/V durations (120.043s video vs 120.004s audio over a 2-min section — no drift) and a perceptually in-sync clip. The export method was NOT the cause (even slow lockstep output-seek was desynced on the bad source).

**CPU hog / apparent hang:** exporting a clip near the end of the VOD ran ffmpeg at ~945% CPU for minutes and made the service unresponsive. Root cause: `-ss` is placed AFTER `-i` in `export/service.py` (output seeking → ffmpeg decodes the entire source from 0 to the clip start) plus a full re-encode with no thread limit. Verified fix: two-stage seek (coarse `-ss` before `-i`, fine `-ss` after `-i`) + a thread cap exported the same clip in ~16s with bounded CPU.

## Problem Frame
- Symptom: exported clips have drifting audio; export pegs all CPU and stalls the service.
- Suspected Layer: worker download format (`source_materializer.py`) and export ffmpeg command (`export/service.py`).
- Touched Contracts: none (same payloads; only the yt-dlp format string and the ffmpeg argument order/flags change).
- Done Criterion: exported clips are A/V-synced and export of a clip from a long VOD completes quickly with bounded CPU.

## Scope
- **Download format (desync fix):** in `worker/src/streamcut_worker/services/source_materializer.py`, prefer a single pre-muxed format and only fall back to the merge path. Change the format to `best[height<=1080]/bestvideo[height<=1080]*+bestaudio` (muxed first, merge as fallback). Keep the rest of the options (concurrent fragments, merge_output_format, etc.) unchanged.
- **Export command (CPU + correctness):** in `worker/src/streamcut_worker/export/service.py`, rebuild the ffmpeg command:
  - Two-stage seek: coarse `-ss <coarse>` BEFORE `-i`, where `coarse = max(0, start_sec - PREROLL)` (PREROLL e.g. 10s), then fine `-ss <start_sec - coarse>` AFTER `-i`, then `-t <duration>`. This is fast (only PREROLL seconds are decoded) and frame-accurate.
  - Add a thread cap (`-threads`) so a single export cannot saturate the host. Use a sane default constant (e.g. 4); expose it as a parameter/constant so it can be tuned, do not leave it unbounded.
  - Add `-avoid_negative_ts make_zero` for clean output timestamps.
  - Keep `-c:v libx264 -preset veryfast -crf 18 -c:a aac -movflags +faststart`.

## Out of Scope
- No source-normalization / CFR re-encode pass — not needed once the muxed format is downloaded (the muxed source is already synced).
- No backend/DB/contract changes.
- No new dependency.
- No change to transcription/silence/loudness/analysis.

## Inputs
- `worker/src/streamcut_worker/services/source_materializer.py` (`YtDlpPlatformDownloader`, options dict line ~53-63)
- `worker/src/streamcut_worker/export/service.py` (`FfmpegClipExportService.export`, command build ~25-45)
- `worker/tests/test_clip_export.py`, `worker/tests/test_source_materializer.py`

## Touched Contracts
- none

## Schema Impact
- none

## Operational Risk
- low — download yields a synced muxed file; export decodes only a short pre-roll and is thread-bounded. Reverting restores prior behavior.

## Rollback Or Migration Note
- none. Note: existing already-downloaded sources were fetched with the merged (drifting) format; a job must be re-downloaded with the new format to get a synced source. Not a migration, just operational re-run.

## Expected Deliverables
- code
- tests

## Constraints
- use existing stack only (yt-dlp, ffmpeg, stdlib); no new dependency
- thread cap must be bounded and tunable, never unbounded
- coarse seek must clamp at 0 when `start_sec < PREROLL`; fine seek must be exact so the clip still starts at `start_sec`
- do not change the export output path, filename, or result contract

## Acceptance Criteria
- the yt-dlp format prefers a single muxed format, with the merge path only as fallback
- the export ffmpeg command uses two-stage seek (coarse before `-i`, fine after) and includes a bounded `-threads` value and `-avoid_negative_ts make_zero`
- for `start_sec < PREROLL`, the command degrades correctly (coarse `-ss 0`, fine `-ss start_sec`) and still produces a clip starting at `start_sec`
- `worker/tests/test_clip_export.py` asserts the new command shape (arg order, thread cap, seek split, both normal and small-start cases)
- `worker/tests/test_source_materializer.py` asserts the muxed-first format string
- existing export result/return behavior is unchanged

## Notes
- Both fixes were validated manually against job 4's source before writing this task: muxed download → A/V durations match within 0.04s over 2 min; two-stage seek export → ~16s with bounded CPU vs minutes at ~945%.
- The `export_test/` folder at repo root holds the manual validation clips; it is throwaway and should not be committed (add to `.gitignore` or delete).
