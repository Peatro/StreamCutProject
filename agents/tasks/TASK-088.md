# TASK-088 Local Qwen Inference Serving In The GPU Worker

## Agent
infra-agent

## Summary
Stand up local Qwen (instruct) inference inside the GPU processing-worker so later tasks can call an LLM for highlight detection without any third-party API. Infra + a thin client only — NO detection logic in this task. The model is private (transcript never leaves the box), has no per-call cost, and reuses the worker's existing GPU.

## Context
See `Documentation/clip-quality-plan.md` (decision: local Qwen on the GPU worker, not Groq hosted). The processing-worker already has a CUDA path for faster-whisper (`docker-compose.gpu.yml`, `WHISPER_DEVICE=cuda`). Qwen will share that GPU. This task delivers the serving + client + config + graceful availability handling; the highlight-detection prompt/logic is TASK-089.

## Problem Frame
- Symptom: there is no local LLM the worker can call.
- Suspected Layer: worker image + a new inference client module + worker config (`worker/Dockerfile`, `worker/src/streamcut_worker/`, compose/env).
- Touched Contracts: none (no backend/transport change; LLM is worker-internal).
- Done Criterion: worker code can call a local `generate(prompt) -> text` against a pinned Qwen model on the GPU, configurable and disable-able, with a clear unavailable-fallback signal.

## Scope
- Choose and PIN a concrete local serving approach suited to a single-box GPU worker (e.g. llama.cpp/llama-cpp-python with a quantized Qwen2.5-Instruct GGUF, or vLLM, or Ollama). Pick the lightest option that runs the model on the existing GPU without fighting whisper for VRAM; justify the choice in the task notes/PR. Pin the model id/quant and the server/runtime version — do not fetch "latest".
- Add a thin client module in the worker exposing a minimal `generate(prompt, *, max_tokens, temperature) -> str` (or a small typed wrapper), with: model id, device, endpoint/runtime params, and an **enable flag** from config/env. When disabled or unavailable, expose a clear signal so callers can fall back to the heuristic (TASK-089 will use this).
- Worker image: install the runtime and make the model available (download/cache at build or first-run; document which). Keep the CPU-first default intact — local Qwen runs only where the GPU override is active, mirroring how whisper-cuda is gated.
- **GPU contention:** ensure Qwen and whisper do not co-resident-OOM. Prefer sequencing within a job (release whisper before loading Qwen, or load Qwen lazily and unload after use). Document the chosen strategy.
- Provide a tiny self-check (e.g. a `--smoke` entrypoint or test that, when the model is present, returns a non-empty completion; when disabled, reports unavailable cleanly).

## Out of Scope
- No highlight-detection prompt, chunking, or candidate logic (that is TASK-089).
- No backend/DB/API/transport change.
- No Groq/hosted path (decision is local). A provider abstraction is NOT required — single local implementation only (avoid speculative abstraction).
- No change to whisper/silence/loudness/export code beyond what GPU sequencing requires.

## Inputs
- `worker/Dockerfile`
- `docker-compose.gpu.yml`, `docker-compose.yml` (how the GPU override + worker roles are wired)
- `worker/src/streamcut_worker/transcription/service.py` (how the CUDA device/model is currently loaded — mirror its gating)
- `worker/src/streamcut_worker/` config/env patterns

## Touched Contracts
- none

## Schema Impact
- none

## Acceptance Criteria
- Worker can call a local `generate(prompt) -> text` against a pinned Qwen model on the GPU.
- An enable flag controls whether local Qwen is active; disabled/unavailable produces a clean, catchable signal (no hard crash) so callers can fall back.
- Model id/quant and runtime version are pinned; CPU-first default path is unchanged.
- The GPU-contention strategy with whisper is implemented and documented.
- A smoke check confirms a non-empty completion when the model is present.

## Constraints
- Local only; pin model + runtime versions; no "latest".
- No provider abstraction for one implementation; minimal client surface.
- Do not regress the CPU-first default or whisper.
- No unrelated refactor.

## Operational Risk
- medium-high — new heavy GPU dependency in the worker image; VRAM pressure with whisper. Gated behind an enable flag and the GPU override; reverting removes the dependency.

## Expected Deliverables
- code
- tests
