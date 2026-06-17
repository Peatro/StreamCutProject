# TASK-076 Add Audio Loudness Signal And Re-Weight Clip Candidate Scoring

## Agent
worker-agent

## Summary
Add an audio-loudness signal to the clip-candidate analyzer and re-weight the scoring formula so genuinely high-energy moments (laughter, shouting, hype) outrank dense monologue, improving highlight selection on long Twitch VODs.

## Context
Sources are 3-4h+ Twitch stream VODs. The primary product goal is automatic clip selection for Shorts/TikTok. The current analyzer (`worker/src/streamcut_worker/analysis/service.py`) scores only transcript text-density + silence:
`score = 0.35·speech_density + 0.25·(1−silence_ratio) + 0.20·emotion(keyword/CAPS/"!") + 0.20·continuity`.
This is effectively a "talks a lot" detector: it rewards sponsor reads and rambling monologue and penalizes the actual highlights (laughter/hype is low word-density and often follows a pause). The extracted audio waveform never reaches the analyzer — only transcript + silence do.

## Problem Frame
- Symptom: selected clip candidates are boring dense-talk segments; real highlights are missed (failure mode A, not boundary quality).
- Suspected Layer: worker candidate analysis (`analysis/service.py`) + missing audio-energy input.
- Touched Contracts: none (loudness is computed and consumed entirely inside the processing worker; the `WorkerProcessingPayload` transport shape is unchanged).
- Done Criterion: a window's audio loudness measurably influences its score, and the scoring weights no longer let talk-density dominate.

## Scope
- Add a loudness measurement step in the worker using the ffmpeg already in the pipeline (no new dependency). The extracted audio path (`audio_result.audio_path`) is available in `WorkerJobRunner.run` at analysis time.
  - Use a single ffmpeg pass over the audio (e.g. `astats` with periodic `reset` via `ametadata=print`, parsing `lavfi.astats.Overall.RMS_level` from stderr — mirror the existing `silence/service.py` ffmpeg-stderr-parsing pattern).
  - Produce a per-second (or fixed small-interval) loudness profile in dB.
- Feed a per-window loudness metric into `CandidateAnalysisRequest` / the analyzer:
  - compute per-window loudness (peak and/or mean of the covered interval),
  - normalize across windows of the same job (same normalization style as `speech_density`/`emotion_hits`).
- Re-weight the scoring formula so interestingness drives selection and density/continuity become quality gates rather than the main driver. Suggested starting weights (tune as the calibration knob):
  `score ≈ 0.45·loudness + 0.20·emotion + 0.20·continuity + 0.15·(1−silence_ratio)`.
  Keep the exact weights as named constants/parameters so they can be tuned without code surgery.
- Add an `AnalysisWindow` field for the normalized loudness so it can be reasoned about in the result object (worker-internal model only — see Out of Scope for persistence).

## Out of Scope
- No backend/DB change: do NOT add a persisted loudness column, do NOT change the `analysisWindows` transport JSON or the Java `AnalysisWindow` entity. The improved selection flows through the existing `totalScore`/candidate fields. Persisting loudness for UI tuning is a possible follow-up, not this task.
- No Twitch chat ingestion (separate future task — chat is the next-strongest signal but needs a separate fetch + dependency).
- No ML laughter/semantic models.
- No clip-boundary refinement (failure mode B) — moments first, boundaries later.
- No new Python dependency. ffmpeg only.

## Inputs
- `worker/src/streamcut_worker/analysis/service.py` (scoring)
- `worker/src/streamcut_worker/analysis/models.py` (`CandidateAnalysisRequest`, `AnalysisWindow`)
- `worker/src/streamcut_worker/silence/service.py` (reference ffmpeg-stderr parsing pattern)
- `worker/src/streamcut_worker/pipeline/job_runner.py` (`run` / `_analyze`, audio path availability)
- `worker/tests/test_candidate_analysis.py`, `worker/tests/test_silence_detection.py` (test patterns)

## Touched Contracts
- none

## Schema Impact
- none

## Operational Risk
- low — one extra ffmpeg pass over already-extracted audio during the processing stage; purely additive to scoring, no recovery/compatibility impact.

## Rollback Or Migration Note
- none — reverting the worker change restores prior scoring; no persisted state changes.

## Expected Deliverables
- code
- tests

## Constraints
- use existing stack only (ffmpeg, stdlib); no new dependency, no numpy/librosa/scipy
- no unrelated refactor; keep the change inside the worker analysis path
- loudness measurement must degrade gracefully: if the ffmpeg loudness pass yields no data, the analyzer must still produce candidates (fall back to the prior signals) rather than fail the job
- keep scoring weights as explicit named constants for later tuning

## Acceptance Criteria
- the analyzer accepts a per-window loudness input and a window's loudness measurably changes its `total_score`
- with the re-weighted formula, a high-loudness window outranks an equal-or-higher talk-density window that is quiet (covered by a unit test with synthetic windows)
- a missing/empty loudness profile does not crash analysis; candidates are still produced
- `worker/tests/test_candidate_analysis.py` covers: loudness raises score, loudness re-weighting beats pure density, and the empty-loudness fallback
- loudness extraction has a focused unit test parsing a representative ffmpeg `astats` stderr sample (mirror `test_silence_detection.py`)
- the transport payload (`WorkerProcessingPayload.analysis_windows`) and backend remain unchanged

## Notes
- This is calibration work: the suggested weights are a starting point. The streamer's own VODs are the ground truth; weights are expected to be tuned. Leave the knob, do not hardcode it away.
- Audio is already extracted for transcription; reuse `audio_result.audio_path`, do not re-extract.
