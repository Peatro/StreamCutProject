# Changelog

All notable changes to StreamCut are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
The release version is the git tag (`vX.Y.Z`) on `main`; `build.gradle.kts`
mirrors it.

## [Unreleased]

_Nothing yet._

## [1.1.0] - 2026-06-29

First release after `v1.0.0`. Backward-compatible feature + fix batch (the
post-1.0 architecture, product-quality, operator-usability, and clip-quality
work). No breaking changes for the operator.

### Added
- **Durable architecture queue (TASK-068..072):** worker task retry/backoff/
  dead-letter semantics, extracted task orchestration + claim flow, dedicated
  export-worker pool, signed-URL artifact delivery, durable object-storage
  artifact contract.
- **Clip-quality track (TASK-087..094):** ground-truth evaluation harness, local
  Qwen inference on the GPU worker, hybrid LLM highlight detection, hook shift to
  the action peak, end-to-end word-level transcript timings, word-by-word karaoke
  burned-in subtitles, vertical 9:16 blurred-fill reframe, clip-local word timings
  in the export-claim payload.
- **Highlight detector & precision layer:** KV-cache quantization + recall-tuned
  prompt; arithmetic rank-fusion engine (chat-density and audio mean-loudness
  scorers; additive ranks, rank-all) — see
  `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md`; audio loudness signal in scoring.
- **Operator-usability wave (TASK-082..086):** in-app delete confirmation, longer
  session lifetime (timeout + remember-me), stable processing labels + percent
  pill, in-place moderated-card update, clip-scoped review player.
- **Product-quality wave (TASK-076..080):** job delete / bulk-clear / complete /
  rest bar, complete-on-moderation with review deletion.
- Twitch VOD source support via TwitchDownloaderCLI (TASK-081), fixing A/V desync
  versus yt-dlp.
- Frontend redesign: theme, job-page layout, compact jobs table, wave progress.
- Evaluation tooling: frozen-request freeze + replay harness, job-21 fixture and
  ground-truth labels, clip-worthiness AUC probe.
- Architecture brief as the source of truth for the highlight pipeline; GitHub
  Actions CI and VPS deploy workflows; project README and favicon.
- GPU worker: whisper device tracking with a GPU override.

### Changed
- Candidate gate: temporal NMS (w=45s) replaces the top-N-by-score cut, and the
  full deduped pool is shown rank-all — detector confidence is proven random with
  respect to clip quality, so it is no longer the sort key.
- Highlight detection greedy-decoded for deterministic, reproducible output.
- Per-stage wall-clock timing in worker logs; progress events trimmed.
- All highlight-pipeline documentation reconciled with the architecture brief.
- Compose stack pinned to project name `streamcut`; NVIDIA driver capabilities
  enabled for the GPU worker; yt-dlp preinstalled with higher fragment concurrency.

### Fixed
- Job delete returning HTTP 500 on jobs with exported clips.
- Hook-shift trimming clips below a minimum length.
- Honest candidate scoring + quality gate (word-level speech density/continuity;
  empty-excerpt and low-score drop).
- GPU worker build (prebuilt CUDA `llama-cpp-python` wheel); absolute ffmpeg path
  passed to TwitchDownloaderCLI.
- CSRF handling: accept the raw XSRF cookie token; stop forcing re-login on 403.
- Karaoke subtitle sync under two-stage seek export; word timings carried in the
  transcript payload; candidate excerpt clipped to words inside the clip window.
- UI: lazy-loaded candidate previews, candidate pagination, no stale signed
  artifact links.

## [1.0.0] - 2026

Initial release (git tag `v1.0.0`). Baseline; predates this changelog — see git
history for details.

[Unreleased]: https://github.com/Peatro/StreamCutProject/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/Peatro/StreamCutProject/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Peatro/StreamCutProject/releases/tag/v1.0.0
