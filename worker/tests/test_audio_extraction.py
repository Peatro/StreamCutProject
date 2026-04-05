from pathlib import Path
import sys
from tempfile import NamedTemporaryFile, TemporaryDirectory

import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.audio import (
    AudioExtractionException,
    AudioExtractionRequest,
    AudioExtractionResult,
    FfmpegAudioExtractionService,
    ProcessExecutionResult,
)


class FakeRunner:
    def __init__(self, result: ProcessExecutionResult) -> None:
        self.result = result
        self.commands: list[list[str]] = []

    def run(self, command):
        self.commands.append(list(command))
        return self.result


class AudioExtractionServiceTests(unittest.TestCase):
    def test_extract_audio_builds_expected_command_and_result(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            video_path = Path(tmp.name)
            request = AudioExtractionRequest(
                video_path=video_path,
                output_dir=Path(temp_dir) / "audio",
            )
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegAudioExtractionService(runner)

            result = service.extract(request)

        self.assertIsInstance(result, AudioExtractionResult)
        self.assertEqual(result.video_path, video_path)
        self.assertEqual(result.audio_path.name, f"{video_path.stem}.wav")
        self.assertEqual(runner.commands[0][0], "ffmpeg")
        self.assertIn("-vn", runner.commands[0])
        self.assertIn(str(video_path), runner.commands[0])
        self.assertEqual(result.returncode, 0)

    def test_extract_audio_raises_useful_error_on_failure(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mov") as tmp:
            tmp.write(b"dummy")
            video_path = Path(tmp.name)
            request = AudioExtractionRequest(
                video_path=video_path,
                output_dir=Path(temp_dir) / "audio",
            )
            runner = FakeRunner(ProcessExecutionResult(1, "", "boom"))
            service = FfmpegAudioExtractionService(runner)

            with self.assertRaises(AudioExtractionException) as ctx:
                service.extract(request)

        self.assertEqual(ctx.exception.returncode, 1)
        self.assertIn("ffmpeg failed", str(ctx.exception))
        self.assertEqual(ctx.exception.stderr, "boom")

    def test_missing_input_file_fails_fast(self):
        runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
        service = FfmpegAudioExtractionService(runner)

        with TemporaryDirectory() as temp_dir:
            with self.assertRaises(AudioExtractionException) as ctx:
                service.extract(
                    AudioExtractionRequest(
                        video_path=Path("missing-file.mp4"),
                        output_dir=Path(temp_dir) / "audio",
                    )
                )

        self.assertIn("does not exist", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
