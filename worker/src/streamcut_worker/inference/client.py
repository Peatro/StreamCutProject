"""Thin local-LLM client for Qwen inference on the GPU worker.

Serving approach: llama-cpp-python (pinned 0.3.12) loading a quantized
Qwen2.5-7B-Instruct GGUF directly into the process.  No separate server,
no HTTP overhead, no Ollama daemon.

Why llama-cpp-python over alternatives:
  - Lighter than vLLM (no torch dependency, no separate process).
  - No daemon to manage (unlike Ollama).
  - Single pip install; loads/unloads the model on demand so we can
    sequence GPU memory with faster-whisper (see _load / _unload).
  - Q4_K_M quant of the 7B model fits in ~5 GB VRAM.

GPU contention strategy:
  The model is loaded lazily on the first generate() call and can be
  explicitly unloaded via unload().  The job runner should:
    1. Run whisper transcription (whisper holds VRAM).
    2. After transcription completes, call generate() — the model loads
       into VRAM on demand.
    3. After LLM work is done, call unload() to free VRAM before the
       next job's whisper pass.
  This ensures whisper and Qwen never co-reside in VRAM.

Pinned versions:
  - Runtime: llama-cpp-python == 0.3.12
  - Model:   Qwen2.5-7B-Instruct-Q4_K_M.gguf
              from repo Qwen/Qwen2.5-7B-Instruct-GGUF on HuggingFace
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Pinned constants
# ---------------------------------------------------------------------------
PINNED_MODEL_REPO = "Qwen/Qwen2.5-7B-Instruct-GGUF"
PINNED_MODEL_FILENAME = "qwen2.5-7b-instruct-q4_k_m.gguf"
PINNED_LLAMA_CPP_VERSION = "0.3.12"

# Default generation parameters
DEFAULT_MAX_TOKENS = 512
DEFAULT_TEMPERATURE = 0.3

# GPU layers — -1 means offload everything to GPU
DEFAULT_N_GPU_LAYERS = -1
# Context window — 4096 is sufficient for transcript-chunk prompts
DEFAULT_N_CTX = 4096


class LlmUnavailableError(Exception):
    """Raised when the local LLM is disabled or cannot be loaded.

    Callers (e.g. TASK-089 highlight detection) should catch this and
    fall back to the heuristic path.  This is the *only* signal they
    need to handle — it is never a hard crash.
    """


@dataclass(slots=True)
class LlmClient:
    """Minimal local-LLM client wrapping llama-cpp-python.

    Lifecycle:
      - Created via ``create_llm_client()``.
      - ``generate()`` loads the model lazily on first call.
      - ``unload()`` releases VRAM (safe to call multiple times).
      - ``is_available`` property tells callers whether the client can
        serve requests (enabled + runtime importable + model file exists).
    """

    enabled: bool
    model_path: Path
    n_gpu_layers: int = DEFAULT_N_GPU_LAYERS
    n_ctx: int = DEFAULT_N_CTX
    _model: Any = field(default=None, init=False, repr=False)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    @property
    def is_available(self) -> bool:
        """Return True when the client is enabled, the runtime is
        importable, and the model file exists on disk."""
        if not self.enabled:
            return False
        if not self.model_path.exists():
            return False
        try:
            import llama_cpp  # noqa: F401
            return True
        except ImportError:
            return False

    def generate(
        self,
        prompt: str,
        *,
        max_tokens: int = DEFAULT_MAX_TOKENS,
        temperature: float = DEFAULT_TEMPERATURE,
    ) -> str:
        """Generate a completion for *prompt*.

        Raises ``LlmUnavailableError`` when the LLM is disabled, the
        runtime is missing, or the model file does not exist.
        """
        if not self.enabled:
            raise LlmUnavailableError("Local LLM is disabled (QWEN_ENABLED=false)")

        self._ensure_loaded()
        assert self._model is not None  # guaranteed by _ensure_loaded

        logger.debug(
            "llm_generate prompt_len=%d max_tokens=%d temperature=%.2f",
            len(prompt),
            max_tokens,
            temperature,
        )

        try:
            output = self._model.create_chat_completion(
                messages=[{"role": "user", "content": prompt}],
                max_tokens=max_tokens,
                temperature=temperature,
            )
        except Exception as exc:
            raise LlmUnavailableError(f"Qwen inference failed: {exc}") from exc

        text: str = output["choices"][0]["message"]["content"]  # type: ignore[index]
        logger.debug("llm_generate result_len=%d", len(text))
        return text

    def unload(self) -> None:
        """Release the model from memory / VRAM.  Safe to call when
        already unloaded."""
        if self._model is not None:
            logger.info("llm_unload releasing Qwen model from VRAM")
            del self._model
            self._model = None
            # Nudge Python + CUDA to reclaim memory
            try:
                import gc
                gc.collect()
            except Exception:  # pragma: no cover
                pass

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------

    def _ensure_loaded(self) -> None:
        """Load the GGUF model into VRAM if not already loaded."""
        if self._model is not None:
            return

        if not self.model_path.exists():
            raise LlmUnavailableError(
                f"Model file not found: {self.model_path} — "
                "download it or set QWEN_ENABLED=false"
            )

        try:
            from llama_cpp import Llama
        except ImportError as exc:
            raise LlmUnavailableError(
                "llama-cpp-python is not installed "
                f"(pinned {PINNED_LLAMA_CPP_VERSION}): {exc}"
            ) from exc

        logger.info(
            "llm_load model=%s n_gpu_layers=%d n_ctx=%d",
            self.model_path,
            self.n_gpu_layers,
            self.n_ctx,
        )

        try:
            self._model = Llama(
                model_path=str(self.model_path),
                n_gpu_layers=self.n_gpu_layers,
                n_ctx=self.n_ctx,
                verbose=False,
                chat_format="chatml",
            )
        except Exception as exc:
            raise LlmUnavailableError(
                f"Failed to load Qwen model: {exc}"
            ) from exc

        logger.info("llm_load complete — model ready")


# ---------------------------------------------------------------------------
# Factory
# ---------------------------------------------------------------------------

def _resolve_model_path(model_dir: str | None) -> Path:
    """Resolve the full path to the GGUF file.

    Search order:
      1. ``$QWEN_MODEL_PATH`` (explicit full path to the .gguf file)
      2. ``<model_dir>/PINNED_MODEL_FILENAME`` where model_dir defaults
         to ``$HF_HOME`` or ``/app/model-cache``
    """
    explicit = os.getenv("QWEN_MODEL_PATH")
    if explicit:
        return Path(explicit)

    base = Path(model_dir) if model_dir else Path(os.getenv("HF_HOME", "/app/model-cache"))
    return base / PINNED_MODEL_FILENAME


def create_llm_client(
    *,
    enabled: bool | None = None,
    model_dir: str | None = None,
    n_gpu_layers: int | None = None,
    n_ctx: int | None = None,
) -> LlmClient:
    """Create an ``LlmClient`` from environment / explicit args.

    Environment variables (all optional, with sane defaults):
      - ``QWEN_ENABLED``    — "true" to activate; default "false".
      - ``QWEN_MODEL_PATH`` — explicit path to the .gguf file.
      - ``QWEN_N_GPU_LAYERS`` — GPU offload layers; default -1 (all).
      - ``QWEN_N_CTX``      — context window size; default 4096.
    """
    if enabled is None:
        enabled = os.getenv("QWEN_ENABLED", "false").strip().lower() in ("1", "true", "yes")

    resolved_gpu_layers = (
        n_gpu_layers
        if n_gpu_layers is not None
        else int(os.getenv("QWEN_N_GPU_LAYERS", str(DEFAULT_N_GPU_LAYERS)))
    )

    resolved_ctx = (
        n_ctx
        if n_ctx is not None
        else int(os.getenv("QWEN_N_CTX", str(DEFAULT_N_CTX)))
    )

    model_path = _resolve_model_path(model_dir)

    client = LlmClient(
        enabled=enabled,
        model_path=model_path,
        n_gpu_layers=resolved_gpu_layers,
        n_ctx=resolved_ctx,
    )

    logger.info(
        "llm_client_created enabled=%s model_path=%s is_available=%s",
        enabled,
        model_path,
        client.is_available,
    )

    return client
