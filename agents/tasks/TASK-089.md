# TASK-089 Hybrid LLM Highlight Detection Logic

## Agent
worker-agent

## Summary
Use the local Qwen client (TASK-088) to decide what is worth cutting: walk the transcript in chunks, feed each chunk's text PLUS the existing heuristic signals (loudness/emotion peaks, silence boundaries) as hints, and have the LLM identify highlight-worthy moments with start/end + reason + confidence. Merge across chunks into clip candidates. The heuristic becomes a hint/feature, not the gate. Measure the result against the TASK-087 ground-truth.

## Context
See `Documentation/clip-quality-plan.md` (decision: hybrid — LLM walks transcript chunks, heuristic peaks injected as hints). This is the product brain. The current detector (`analysis/service.py`) is a pure sliding-window heuristic; keep it as the fallback and as the hint source. Depends on TASK-088 (local Qwen `generate`). Tuned/measured with TASK-087.

## Problem Frame
- Symptom: candidate selection is a content-blind heuristic; it cannot tell a funny/hype moment from dense monologue.
- Suspected Layer: worker analysis (`worker/src/streamcut_worker/analysis/`) + the local Qwen client from TASK-088.
- Touched Contracts: candidate generation is worker-internal; `ClipCandidate` shape is reused (confidence -> score; reason kept internal in v1).
- Done Criterion: for a transcript with heuristic hints, the detector returns highlight candidates chosen by the LLM, measurably hitting the ground-truth set better than the heuristic alone, behind a flag with heuristic fallback.

## Scope
- Chunk the transcript into coherent, OVERLAPPING chunks (overlap so a moment split across a boundary is not lost). Size chunks so a 3h VOD costs tens of LLM calls, not hundreds (no per-5s-window calls).
- For each chunk, build a prompt containing: the chunk transcript (with timestamps) + injected heuristic hints for that chunk (loudness peak times/levels via the existing loudness profile, emotion hits, silence boundaries). Ask Qwen to return highlight moments as structured output: `start_sec`, `end_sec`, `reason`, `confidence`. Parse robustly (tolerate minor format drift).
- Merge/dedupe moments across overlapping chunks (combine or pick the higher-confidence of temporally-overlapping picks). Produce `ClipCandidate`s: map confidence -> `score`; keep `reason` internal for now (do NOT change the candidate transport/DB contract in this task).
- Wire behind an **enable flag**: when local Qwen is disabled/unavailable (TASK-088 signal) or detection errors, fall back to the existing heuristic `analyze_candidates` so the pipeline never hard-fails.
- Validate against TASK-087: include a way to run the detector over a ground-truth source and report the hit-rate, and record a before/after (heuristic vs hybrid) number in the PR.

## Out of Scope
- No backend/DB/API/transport/contract change (candidate payload shape stays; `reason` stays worker-internal in v1).
- No serving/infra work (that is TASK-088).
- No UI surfacing of reason/confidence (later, optional).
- No multimodal (audio/video frame) input — text + loudness hints only in v1.
- Do not delete the heuristic; it is the fallback and the hint source.

## Inputs
- `worker/src/streamcut_worker/analysis/service.py` and `analysis/models.py` (heuristic, `ClipCandidate`, loudness/silence inputs)
- TASK-088 local Qwen client
- `worker/eval/` harness from TASK-087
- `worker/tests/test_candidate_analysis.py`

## Touched Contracts
- none (candidate shape reused; reason/confidence internal)

## Schema Impact
- none

## Acceptance Criteria
- Transcript is chunked with overlap; per-chunk prompts include heuristic hints (loudness/emotion/silence).
- LLM output is parsed into candidates (start/end/reason/confidence); merge/dedupe across chunks works.
- Behind an enable flag with a clean fallback to the heuristic when Qwen is unavailable or detection errors.
- Cost is bounded (tens of LLM calls for a multi-hour VOD, not hundreds) — document the chunking math.
- Measured against TASK-087 ground-truth with a before/after hit-rate recorded; tests cover chunking, parsing, and merge/dedupe with the LLM call mocked (no GPU needed in tests).

## Constraints
- Reuse the heuristic and loudness/silence models; do not duplicate.
- LLM call must be mockable in tests; no real model required to run the suite.
- Keep candidate contract unchanged in this task.
- No unrelated refactor.

## Expected Deliverables
- code
- tests
