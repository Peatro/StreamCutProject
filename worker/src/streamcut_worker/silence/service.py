from __future__ import annotations

import re

from .exceptions import SilenceDetectionException
from .models import SilenceDetectionRequest, SilenceDetectionResult, SilenceInterval
from .process import ProcessRunner, SubprocessProcessRunner

_SILENCE_START_RE = re.compile(r"silence_start:\s*(?P<start>\d+(?:\.\d+)?)")
_SILENCE_END_RE = re.compile(
    r"silence_end:\s*(?P<end>\d+(?:\.\d+)?)\s*\|\s*silence_duration:\s*(?P<duration>\d+(?:\.\d+)?)"
)


class FfmpegSilenceDetectionService:
    def __init__(self, runner: ProcessRunner | None = None) -> None:
        self._runner = runner or SubprocessProcessRunner()

    def detect(self, request: SilenceDetectionRequest) -> SilenceDetectionResult:
        if not request.video_path.exists():
            raise SilenceDetectionException(
                f"Input video does not exist: {request.video_path}",
            )

        command = [
            "ffmpeg",
            "-i",
            str(request.video_path),
            "-af",
            f"silencedetect=noise={request.noise_threshold_db}:d={request.minimum_duration_sec}",
            "-f",
            "null",
            "-",
        ]

        execution_result = self._runner.run(command)
        if execution_result.returncode != 0:
            raise SilenceDetectionException(
                "ffmpeg failed while detecting silence",
                command=list(command),
                returncode=execution_result.returncode,
                stderr=execution_result.stderr.strip() or None,
            )

        return SilenceDetectionResult(
            video_path=request.video_path,
            command=list(command),
            returncode=execution_result.returncode,
            stdout=execution_result.stdout,
            stderr=execution_result.stderr,
            silence_segments=self._parse_silence_segments(execution_result.stderr),
        )

    def _parse_silence_segments(self, stderr: str) -> list[SilenceInterval]:
        intervals: list[SilenceInterval] = []
        current_start: float | None = None

        for line in stderr.splitlines():
            start_match = _SILENCE_START_RE.search(line)
            if start_match is not None:
                current_start = float(start_match.group("start"))
                continue

            end_match = _SILENCE_END_RE.search(line)
            if end_match is not None and current_start is not None:
                intervals.append(
                    SilenceInterval(
                        start_sec=current_start,
                        end_sec=float(end_match.group("end")),
                        duration_sec=float(end_match.group("duration")),
                    )
                )
                current_start = None

        return intervals
