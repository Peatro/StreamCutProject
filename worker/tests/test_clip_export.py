from pathlib import Path
import sys
from tempfile import NamedTemporaryFile, TemporaryDirectory

import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.export import (
    ClipExportException,
    ClipExportRequest,
    FfmpegClipExportService,
    ProcessExecutionResult,
)


class FakeRunner:
    def __init__(self, result: ProcessExecutionResult) -> None:
        self.result = result
        self.commands: list[list[str]] = []

    def run(self, command):
        self.commands.append(list(command))
        return self.result


class ClipExportServiceTests(unittest.TestCase):
    def test_export_builds_expected_command_and_result(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            result = service.export(
                ClipExportRequest(
                    job_id="job-1",
                    candidate_id="7",
                    source_video_path=source_path,
                    start_sec=5.25,
                    end_sec=12.75,
                )
            )

            expected_path = Path(temp_dir) / "artifacts" / "jobs" / "job-1" / "exports" / "candidate-7.mp4"
            self.assertEqual(result.artifact_path, expected_path)
            self.assertEqual(result.status, "COMPLETED")
            self.assertEqual(result.duration_sec, 7.5)
            self.assertEqual(runner.commands[0][0], "ffmpeg")
            self.assertIn("-ss", runner.commands[0])
            self.assertIn("-t", runner.commands[0])
            self.assertIn(str(expected_path), runner.commands[0])
            self.assertTrue(expected_path.parent.exists())

    def test_export_raises_useful_error_on_failure(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mkv") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(1, "", "boom"))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            with self.assertRaises(ClipExportException) as ctx:
                service.export(
                    ClipExportRequest(
                        job_id="job-1",
                        candidate_id="7",
                        source_video_path=source_path,
                        start_sec=1.0,
                        end_sec=3.0,
                    )
                )

        self.assertEqual(ctx.exception.returncode, 1)
        self.assertIn("ffmpeg failed", str(ctx.exception))
        self.assertEqual(ctx.exception.stderr, "boom")

    def test_missing_input_file_fails_fast(self):
        runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
        service = FfmpegClipExportService(Path("/tmp/artifacts"), runner)

        with self.assertRaises(ClipExportException) as ctx:
            service.export(
                ClipExportRequest(
                    job_id="job-1",
                    candidate_id="7",
                    source_video_path=Path("missing.mp4"),
                    start_sec=1.0,
                    end_sec=3.0,
                )
            )

        self.assertIn("does not exist", str(ctx.exception))

    def test_invalid_time_range_fails_fast(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            with self.assertRaises(ClipExportException) as ctx:
                service.export(
                    ClipExportRequest(
                        job_id="job-1",
                        candidate_id="7",
                        source_video_path=source_path,
                        start_sec=10.0,
                        end_sec=5.0,
                    )
                )

        self.assertIn("end_sec must be greater", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
