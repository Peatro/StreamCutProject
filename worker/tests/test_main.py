from pathlib import Path
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker import main as worker_main


class WorkerMainTests(unittest.TestCase):
    def test_processing_role_loads_transcription_model_and_reports_whisper_device(self) -> None:
        with self._patched_environment(
            WORKER_ROLE="processing",
            WORKER_ID="processing-worker-1",
            WHISPER_DEVICE="cuda",
            WHISPER_COMPUTE_TYPE="float16",
        ), mock.patch.object(worker_main.signal, "signal"), mock.patch.object(
            worker_main, "create_default_job_runner", return_value=object()
        ) as create_runner, mock.patch.object(worker_main, "WorkerPollingLoop") as polling_loop:
            polling_loop.return_value.run_forever.return_value = None

            worker_main.main()

        create_runner.assert_called_once_with(
            storage_root=Path("/data/storage"),
            emotion_keywords=(),
            load_transcription_model=True,
            whisper_device="cuda",
            whisper_compute_type="float16",
        )
        polling_loop.assert_called_once()
        self.assertEqual(polling_loop.call_args.kwargs["worker_role"], "processing")
        self.assertEqual(polling_loop.call_args.kwargs["whisper_device"], "cuda")

    def test_export_role_skips_transcription_model_cache_and_whisper_reporting(self) -> None:
        with self._patched_environment(
            WORKER_ROLE="export",
            WORKER_ID="export-worker-1",
            WHISPER_DEVICE="cuda",
        ), mock.patch.object(worker_main.signal, "signal"), mock.patch.object(
            worker_main, "create_default_job_runner", return_value=object()
        ) as create_runner, mock.patch.object(worker_main, "WorkerPollingLoop") as polling_loop:
            polling_loop.return_value.run_forever.return_value = None

            worker_main.main()

        create_runner.assert_called_once_with(
            storage_root=Path("/data/storage"),
            emotion_keywords=(),
            load_transcription_model=False,
            whisper_device="cuda",
            whisper_compute_type="int8",
        )
        polling_loop.assert_called_once()
        self.assertEqual(polling_loop.call_args.kwargs["worker_role"], "export")
        self.assertIsNone(polling_loop.call_args.kwargs["whisper_device"])

    def test_rejects_unknown_worker_role(self) -> None:
        with self._patched_environment(WORKER_ROLE="invalid"), mock.patch.object(worker_main.signal, "signal"):
            with self.assertRaisesRegex(
                ValueError,
                "WORKER_ROLE must be 'download', 'processing', or 'export'",
            ):
                worker_main.main()

    @staticmethod
    def _patched_environment(**overrides: str):
        base_environment = {
            "BACKEND_BASE_URL": "http://backend:8080",
            "APP_STORAGE_LOCAL_ROOT": "/data/storage",
            "WORKER_ID": "worker-1",
            "WORKER_ROLE": "download",
            "WORKER_POLL_INTERVAL_SEC": "0",
            "WORKER_EMOTION_KEYWORDS": "",
            "WHISPER_DEVICE": "cpu",
            "WHISPER_COMPUTE_TYPE": "int8",
        }
        base_environment.update(overrides)
        return mock.patch.dict(os.environ, base_environment, clear=True)


if __name__ == "__main__":
    unittest.main()
