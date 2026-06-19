"""Tests for the local Qwen LLM inference client (TASK-088).

All tests are fully mocked — no GPU, no model file, no llama-cpp-python
runtime required.
"""

from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.inference.client import (
    DEFAULT_MAX_TOKENS,
    DEFAULT_N_CTX,
    DEFAULT_N_GPU_LAYERS,
    DEFAULT_TEMPERATURE,
    LlmClient,
    LlmUnavailableError,
    PINNED_MODEL_FILENAME,
    create_llm_client,
)


class LlmClientDisabledTests(unittest.TestCase):
    """When disabled, generate() raises LlmUnavailableError cleanly."""

    def test_generate_raises_when_disabled(self) -> None:
        client = LlmClient(enabled=False, model_path=Path("/dummy/model.gguf"))
        with self.assertRaises(LlmUnavailableError) as ctx:
            client.generate("Hello")
        self.assertIn("disabled", str(ctx.exception))

    def test_is_available_false_when_disabled(self) -> None:
        client = LlmClient(enabled=False, model_path=Path("/dummy/model.gguf"))
        self.assertFalse(client.is_available)

    def test_unload_is_safe_when_not_loaded(self) -> None:
        client = LlmClient(enabled=False, model_path=Path("/dummy/model.gguf"))
        # Should not raise
        client.unload()


class LlmClientMissingModelTests(unittest.TestCase):
    """When enabled but the model file is absent."""

    def test_generate_raises_when_model_missing(self) -> None:
        client = LlmClient(enabled=True, model_path=Path("/nonexistent/model.gguf"))
        with self.assertRaises(LlmUnavailableError) as ctx:
            client.generate("Hello")
        self.assertIn("not found", str(ctx.exception))

    def test_is_available_false_when_model_missing(self) -> None:
        client = LlmClient(enabled=True, model_path=Path("/nonexistent/model.gguf"))
        self.assertFalse(client.is_available)


class LlmClientMissingRuntimeTests(unittest.TestCase):
    """When enabled + model exists but llama_cpp is not installed."""

    def test_generate_raises_on_import_error(self) -> None:
        client = LlmClient(enabled=True, model_path=Path("/dummy/model.gguf"))
        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": None}):
            with self.assertRaises(LlmUnavailableError) as ctx:
                client.generate("Hello")
            self.assertIn("not installed", str(ctx.exception))

    def test_is_available_false_on_import_error(self) -> None:
        client = LlmClient(enabled=True, model_path=Path("/dummy/model.gguf"))
        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": None}):
            self.assertFalse(client.is_available)


class LlmClientGenerateTests(unittest.TestCase):
    """When everything is available, generate() returns a non-empty string."""

    def _make_client_with_mock_model(self) -> tuple[LlmClient, mock.MagicMock]:
        fake_llama_cls = mock.MagicMock()
        fake_model_instance = mock.MagicMock()
        fake_model_instance.create_chat_completion.return_value = {
            "choices": [{"message": {"content": "This is a highlight moment."}}]
        }
        fake_llama_cls.return_value = fake_model_instance

        fake_llama_module = mock.MagicMock()
        fake_llama_module.Llama = fake_llama_cls

        client = LlmClient(enabled=True, model_path=Path("/dummy/model.gguf"))
        return client, fake_llama_module

    def test_generate_returns_nonempty_text(self) -> None:
        client, fake_llama_module = self._make_client_with_mock_model()

        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": fake_llama_module}):
            result = client.generate("Identify highlights in this transcript.")

        self.assertIsInstance(result, str)
        self.assertTrue(len(result) > 0)
        self.assertEqual(result, "This is a highlight moment.")

    def test_generate_passes_params(self) -> None:
        client, fake_llama_module = self._make_client_with_mock_model()

        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": fake_llama_module}):
            client.generate("test", max_tokens=128, temperature=0.7)

        model_instance = fake_llama_module.Llama.return_value
        call_kwargs = model_instance.create_chat_completion.call_args
        self.assertEqual(call_kwargs.kwargs["max_tokens"], 128)
        self.assertAlmostEqual(call_kwargs.kwargs["temperature"], 0.7)

    def test_generate_uses_chatml_user_message(self) -> None:
        client, fake_llama_module = self._make_client_with_mock_model()

        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": fake_llama_module}):
            client.generate("my prompt")

        model_instance = fake_llama_module.Llama.return_value
        call_kwargs = model_instance.create_chat_completion.call_args
        messages = call_kwargs.kwargs["messages"]
        self.assertEqual(len(messages), 1)
        self.assertEqual(messages[0]["role"], "user")
        self.assertEqual(messages[0]["content"], "my prompt")

    def test_unload_clears_model(self) -> None:
        client, fake_llama_module = self._make_client_with_mock_model()

        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": fake_llama_module}):
            client.generate("test")
            self.assertIsNotNone(client._model)
            client.unload()
            self.assertIsNone(client._model)

    def test_lazy_load_only_once(self) -> None:
        client, fake_llama_module = self._make_client_with_mock_model()

        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": fake_llama_module}):
            client.generate("call1")
            client.generate("call2")

        # Llama constructor called only once (lazy load)
        self.assertEqual(fake_llama_module.Llama.call_count, 1)

    def test_inference_error_raises_unavailable(self) -> None:
        client, fake_llama_module = self._make_client_with_mock_model()
        model_instance = fake_llama_module.Llama.return_value
        model_instance.create_chat_completion.side_effect = RuntimeError("CUDA OOM")

        with mock.patch.object(Path, "exists", return_value=True), \
             mock.patch.dict("sys.modules", {"llama_cpp": fake_llama_module}):
            with self.assertRaises(LlmUnavailableError) as ctx:
                client.generate("test")
            self.assertIn("inference failed", str(ctx.exception))


class CreateLlmClientTests(unittest.TestCase):
    """Factory function tests."""

    def test_disabled_by_default(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True):
            client = create_llm_client()
        self.assertFalse(client.enabled)

    def test_enabled_from_env(self) -> None:
        with mock.patch.dict(os.environ, {"QWEN_ENABLED": "true"}, clear=True):
            client = create_llm_client()
        self.assertTrue(client.enabled)

    def test_explicit_path_from_env(self) -> None:
        env = {
            "QWEN_ENABLED": "true",
            "QWEN_MODEL_PATH": "/custom/path/model.gguf",
        }
        with mock.patch.dict(os.environ, env, clear=True):
            client = create_llm_client()
        self.assertEqual(client.model_path, Path("/custom/path/model.gguf"))

    def test_default_model_path_uses_hf_home(self) -> None:
        env = {
            "HF_HOME": "/model-cache",
        }
        with mock.patch.dict(os.environ, env, clear=True):
            client = create_llm_client()
        self.assertEqual(
            client.model_path,
            Path("/model-cache") / PINNED_MODEL_FILENAME,
        )

    def test_custom_gpu_layers_from_env(self) -> None:
        env = {"QWEN_N_GPU_LAYERS": "32"}
        with mock.patch.dict(os.environ, env, clear=True):
            client = create_llm_client()
        self.assertEqual(client.n_gpu_layers, 32)

    def test_custom_ctx_from_env(self) -> None:
        env = {"QWEN_N_CTX": "8192"}
        with mock.patch.dict(os.environ, env, clear=True):
            client = create_llm_client()
        self.assertEqual(client.n_ctx, 8192)

    def test_explicit_kwargs_override_env(self) -> None:
        with mock.patch.dict(os.environ, {"QWEN_N_GPU_LAYERS": "32"}, clear=True):
            client = create_llm_client(enabled=True, n_gpu_layers=16, n_ctx=2048)
        self.assertTrue(client.enabled)
        self.assertEqual(client.n_gpu_layers, 16)
        self.assertEqual(client.n_ctx, 2048)


if __name__ == "__main__":
    unittest.main()
