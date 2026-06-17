from pathlib import Path
import sys
from tempfile import NamedTemporaryFile, TemporaryDirectory

import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.loudness import (
    FfmpegLoudnessDetectionService,
    LoudnessDetectionException,
    LoudnessDetectionRequest,
)
from streamcut_worker.silence import ProcessExecutionResult


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


REPRESENTATIVE_ASTATS_STDERR = """\
[Parsed_ametadata_1 @ 0x55b] frame:0    pts:0       pts_time:0
[Parsed_ametadata_1 @ 0x55b] lavfi.astats.Overall.RMS_level=-25.432100
[Parsed_ametadata_1 @ 0x55b] frame:1    pts:44100   pts_time:1
[Parsed_ametadata_1 @ 0x55b] lavfi.astats.Overall.RMS_level=-18.765400
[Parsed_ametadata_1 @ 0x55b] frame:2    pts:88200   pts_time:2
[Parsed_ametadata_1 @ 0x55b] lavfi.astats.Overall.RMS_level=-inf
[Parsed_ametadata_1 @ 0x55b] frame:3    pts:132300  pts_time:3
[Parsed_ametadata_1 @ 0x55b] lavfi.astats.Overall.RMS_level=-12.345600
""".strip()


class LoudnessDetectionServiceTests(unittest.TestCase):
    def test_detect_parses_multiple_rms_samples(self) -> None:
        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
            tmp.write(b"dummy")
            audio_path = Path(tmp.name)
            service = FfmpegLoudnessDetectionService(
                FakeRunner(returncode=0, stdout="", stderr=REPRESENTATIVE_ASTATS_STDERR)
            )

            result = service.detect(
                LoudnessDetectionRequest(audio_path=audio_path, interval_sec=1.0)
            )

        self.assertEqual(len(result.samples), 4)
        self.assertAlmostEqual(result.samples[0].time_sec, 0.0)
        self.assertAlmostEqual(result.samples[0].rms_db, -25.4321)
        self.assertAlmostEqual(result.samples[1].time_sec, 1.0)
        self.assertAlmostEqual(result.samples[1].rms_db, -18.7654)
        self.assertAlmostEqual(result.samples[2].time_sec, 2.0)
        self.assertAlmostEqual(result.samples[2].rms_db, -100.0)  # -inf mapped to -100
        self.assertAlmostEqual(result.samples[3].time_sec, 3.0)
        self.assertAlmostEqual(result.samples[3].rms_db, -12.3456)
        self.assertEqual(result.command[0], "ffmpeg")

    def test_detect_builds_correct_ffmpeg_command(self) -> None:
        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
            tmp.write(b"dummy")
            audio_path = Path(tmp.name)
            runner = FakeRunner(returncode=0, stdout="", stderr="")
            service = FfmpegLoudnessDetectionService(runner)

            service.detect(LoudnessDetectionRequest(audio_path=audio_path, interval_sec=2.0))

        self.assertEqual(len(runner.commands), 1)
        cmd = runner.commands[0]
        self.assertEqual(cmd[0], "ffmpeg")
        self.assertIn("-af", cmd)
        af_index = cmd.index("-af")
        af_value = cmd[af_index + 1]
        self.assertIn("astats=metadata=1:reset=2.0", af_value)
        self.assertIn("ametadata=mode=print:key=lavfi.astats.Overall.RMS_level", af_value)

    def test_detect_raises_on_ffmpeg_failure(self) -> None:
        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
            tmp.write(b"dummy")
            audio_path = Path(tmp.name)
            service = FfmpegLoudnessDetectionService(
                FakeRunner(returncode=1, stdout="", stderr="error: bad input")
            )

            with self.assertRaises(LoudnessDetectionException) as ctx:
                service.detect(LoudnessDetectionRequest(audio_path=audio_path))

        self.assertEqual(ctx.exception.returncode, 1)
        self.assertIn("ffmpeg failed", str(ctx.exception))

    def test_detect_raises_on_missing_file(self) -> None:
        service = FfmpegLoudnessDetectionService(FakeRunner())

        with self.assertRaises(LoudnessDetectionException) as ctx:
            service.detect(LoudnessDetectionRequest(audio_path=Path("missing-file.wav")))

        self.assertIn("does not exist", str(ctx.exception))

    def test_detect_returns_empty_samples_on_empty_stderr(self) -> None:
        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
            tmp.write(b"dummy")
            audio_path = Path(tmp.name)
            service = FfmpegLoudnessDetectionService(
                FakeRunner(returncode=0, stdout="", stderr="")
            )

            result = service.detect(LoudnessDetectionRequest(audio_path=audio_path))

        self.assertEqual(len(result.samples), 0)


if __name__ == "__main__":
    unittest.main()
