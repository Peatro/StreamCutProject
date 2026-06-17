from __future__ import annotations

import logging
import re

from .exceptions import LoudnessDetectionException
from .models import LoudnessDetectionRequest, LoudnessDetectionResult, LoudnessSample
from streamcut_worker.silence.process import ProcessRunner, SubprocessProcessRunner

logger = logging.getLogger(__name__)

_RMS_LEVEL_RE = re.compile(
    r"lavfi\.astats\.Overall\.RMS_level=(?P<rms>-?\d+(?:\.\d+)?|-inf)"
)


class FfmpegLoudnessDetectionService:
    def __init__(self, runner: ProcessRunner | None = None) -> None:
        self._runner = runner or SubprocessProcessRunner()

    def detect(self, request: LoudnessDetectionRequest) -> LoudnessDetectionResult:
        if not request.audio_path.exists():
            raise LoudnessDetectionException(
                f"Input audio does not exist: {request.audio_path}",
            )

        interval = max(0.1, request.interval_sec)

        command = [
            "ffmpeg",
            "-i",
            str(request.audio_path),
            "-af",
            (
                f"astats=metadata=1:reset={interval},"
                f"ametadata=mode=print:key=lavfi.astats.Overall.RMS_level"
            ),
            "-f",
            "null",
            "-",
        ]

        execution_result = self._runner.run(command)
        if execution_result.returncode != 0:
            raise LoudnessDetectionException(
                "ffmpeg failed while measuring loudness",
                command=list(command),
                returncode=execution_result.returncode,
                stderr=execution_result.stderr.strip() or None,
            )

        samples = self._parse_rms_samples(execution_result.stderr, interval)

        return LoudnessDetectionResult(
            audio_path=request.audio_path,
            command=list(command),
            returncode=execution_result.returncode,
            stderr=execution_result.stderr,
            samples=samples,
        )

    def _parse_rms_samples(self, stderr: str, interval_sec: float) -> list[LoudnessSample]:
        """Parse RMS level values from ffmpeg ametadata stderr output.

        Each ametadata print line looks like:
            lavfi.astats.Overall.RMS_level=-25.123456
        or for silence:
            lavfi.astats.Overall.RMS_level=-inf

        We assign timestamps based on the sequential index and the interval.
        """
        samples: list[LoudnessSample] = []
        sample_index = 0

        for line in stderr.splitlines():
            match = _RMS_LEVEL_RE.search(line)
            if match is None:
                continue

            rms_str = match.group("rms")
            if rms_str == "-inf":
                rms_db = -100.0
            else:
                rms_db = float(rms_str)

            time_sec = sample_index * interval_sec
            samples.append(LoudnessSample(time_sec=time_sec, rms_db=rms_db))
            sample_index += 1

        return samples
