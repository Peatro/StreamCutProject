from __future__ import annotations

import re
from pathlib import Path

from .exceptions import ClipExportException
from .models import ClipExportRequest, ClipExportResult
from .process import ProcessRunner, SubprocessProcessRunner

_SAFE_COMPONENT_RE = re.compile(r"[^a-zA-Z0-9._-]+")

_SEEK_PREROLL_SEC: float = 10.0
_FFMPEG_THREAD_CAP: int = 4


class FfmpegClipExportService:
    def __init__(self, artifact_root: Path, runner: ProcessRunner | None = None) -> None:
        self._artifact_root = artifact_root
        self._runner = runner or SubprocessProcessRunner()

    def export(self, request: ClipExportRequest) -> ClipExportResult:
        self._validate_request(request)

        artifact_path = self._resolve_output_path(request)
        artifact_path.parent.mkdir(parents=True, exist_ok=True)

        duration_sec = request.end_sec - request.start_sec
        coarse_sec = max(0.0, request.start_sec - _SEEK_PREROLL_SEC)
        fine_sec = request.start_sec - coarse_sec
        command = [
            "ffmpeg",
            "-y",
            "-ss",
            _format_time(coarse_sec),
            "-i",
            str(request.source_video_path),
            "-ss",
            _format_time(fine_sec),
            "-t",
            _format_time(duration_sec),
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "18",
            "-c:a",
            "aac",
            "-threads",
            str(_FFMPEG_THREAD_CAP),
            "-avoid_negative_ts",
            "make_zero",
            "-movflags",
            "+faststart",
            str(artifact_path),
        ]

        execution_result = self._runner.run(command)
        if execution_result.returncode != 0:
            raise ClipExportException(
                "ffmpeg failed while exporting clip",
                command=list(command),
                returncode=execution_result.returncode,
                stderr=execution_result.stderr.strip() or None,
            )

        return ClipExportResult(
            job_id=request.job_id,
            candidate_id=request.candidate_id,
            source_video_path=request.source_video_path,
            artifact_path=artifact_path,
            start_sec=request.start_sec,
            end_sec=request.end_sec,
            duration_sec=duration_sec,
            command=list(command),
            returncode=execution_result.returncode,
            stdout=execution_result.stdout,
            stderr=execution_result.stderr,
        )

    def _validate_request(self, request: ClipExportRequest) -> None:
        if not request.source_video_path.exists():
            raise ClipExportException(
                f"Input video does not exist: {request.source_video_path}",
            )
        if request.start_sec < 0:
            raise ClipExportException(
                f"start_sec must be non-negative: {request.start_sec}",
            )
        if request.end_sec <= request.start_sec:
            raise ClipExportException(
                "end_sec must be greater than start_sec",
            )

    def _resolve_output_path(self, request: ClipExportRequest) -> Path:
        job_component = _normalize_component(request.job_id, "job")
        candidate_component = _normalize_component(request.candidate_id, "candidate")
        filename = f"candidate-{candidate_component}.mp4"
        return self._artifact_root / "jobs" / job_component / "exports" / filename


def _format_time(value: float) -> str:
    return f"{value:.6f}"


def _normalize_component(value: str, fallback: str) -> str:
    safe_name = _SAFE_COMPONENT_RE.sub("_", value.strip()) if value else ""
    if not safe_name:
        return fallback
    return safe_name

