from pathlib import Path
import sys
from tempfile import NamedTemporaryFile, TemporaryDirectory

import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.silence import (
    ProcessExecutionResult,
    FfmpegSilenceDetectionService,
    SilenceDetectionException,
    SilenceDetectionRequest,
)


class FakeRunner:
    def __init__(self, returncode: int = 0, stdout: str = "", stderr: str = "") -> None:
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        self.commands: list[list[str]] = []

    def run(self, command):
        self.commands.append(list(command))
        return ProcessExecutionResult(
            returncode=self.returncode,
            stdout=self.stdout,
            stderr=self.stderr,
        )


class SilenceDetectionServiceTests(unittest.TestCase):
    def test_detect_parses_multiple_silence_intervals(self):
        stderr = """
[silencedetect @ 0x1] silence_start: 0.123
[silencedetect @ 0x1] silence_end: 1.234 | silence_duration: 1.111
[silencedetect @ 0x1] silence_start: 5.0
[silencedetect @ 0x1] silence_end: 7.5 | silence_duration: 2.5
""".strip()

        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            video_path = Path(tmp.name)
            service = FfmpegSilenceDetectionService(
                FakeRunner(returncode=0, stdout="", stderr=stderr)
            )

            result = service.detect(
                SilenceDetectionRequest(
                    video_path=video_path,
                )
            )

        self.assertEqual(len(result.silence_segments), 2)
        self.assertEqual(result.silence_segments[0].start_sec, 0.123)
        self.assertEqual(result.silence_segments[0].end_sec, 1.234)
        self.assertEqual(result.silence_segments[0].duration_sec, 1.111)
        self.assertEqual(result.silence_segments[1].start_sec, 5.0)
        self.assertEqual(result.silence_segments[1].end_sec, 7.5)
        self.assertEqual(result.silence_segments[1].duration_sec, 2.5)
        self.assertEqual(result.command[0], "ffmpeg")

    def test_detect_raises_useful_error_on_failure(self):
        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            video_path = Path(tmp.name)
            service = FfmpegSilenceDetectionService(
                FakeRunner(returncode=1, stdout="", stderr="boom")
            )

            with self.assertRaises(SilenceDetectionException) as ctx:
                service.detect(
                    SilenceDetectionRequest(
                        video_path=video_path,
                    )
                )

        self.assertEqual(ctx.exception.returncode, 1)
        self.assertIn("ffmpeg failed", str(ctx.exception))
        self.assertEqual(ctx.exception.stderr, "boom")

    def test_missing_input_file_fails_fast(self):
        service = FfmpegSilenceDetectionService(FakeRunner())

        with self.assertRaises(SilenceDetectionException) as ctx:
            service.detect(SilenceDetectionRequest(video_path=Path("missing-file.mp4")))

        self.assertIn("does not exist", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
