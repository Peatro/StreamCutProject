from __future__ import annotations

import re
from pathlib import Path

from .exceptions import ClipExportException
from .models import ClipExportRequest, ClipExportResult
from .process import ProcessRunner, SubprocessProcessRunner

_SAFE_COMPONENT_RE = re.compile(r"[^a-zA-Z0-9._-]+")

_SEEK_PREROLL_SEC: float = 10.0
_FFMPEG_THREAD_CAP: int = 4

# --- Vertical reframe (Tier 0 blurred-fill) constants ---
REFRAME_CANVAS_WIDTH: int = 1080
REFRAME_CANVAS_HEIGHT: int = 1920
REFRAME_BLUR_SIGMA: int = 40
# Downscale the background to this width before blurring (keeps blur cheap).
REFRAME_BG_DOWNSCALE_WIDTH: int = 270


def _build_reframe_filter(
    canvas_w: int = REFRAME_CANVAS_WIDTH,
    canvas_h: int = REFRAME_CANVAS_HEIGHT,
    blur_sigma: int = REFRAME_BLUR_SIGMA,
    bg_downscale_w: int = REFRAME_BG_DOWNSCALE_WIDTH,
) -> str:
    """Build a filter_complex string for Tier 0 blurred-fill vertical reframe.

    The graph:
      1. Split the input into two streams.
      2. Background: downscale to bg_downscale_w (cheap), blur, scale up to
         canvas, center-crop to exact canvas dimensions.
      3. Foreground: scale to fit canvas width, preserve aspect ratio.
      4. Overlay foreground centered on background.

    The foreground height is capped at canvas_h so ultra-tall sources don't
    overflow. The overlay y-position uses (H-h)/2 to vertically center the
    source.  Subtitle burning (TASK-092) can later compose on top of the
    [out] pad or after this filter; the 9:16 safe-zone is the foreground
    rectangle centered in the canvas.
    """
    bg_downscale_h = int(bg_downscale_w * canvas_h / canvas_w)
    return (
        f"[0:v]split=2[bg_in][fg_in];"
        f"[bg_in]scale={bg_downscale_w}:{bg_downscale_h}:force_original_aspect_ratio=increase,"
        f"crop={bg_downscale_w}:{bg_downscale_h},"
        f"gblur=sigma={blur_sigma},"
        f"scale={canvas_w}:{canvas_h}[bg];"
        f"[fg_in]scale={canvas_w}:-2:force_original_aspect_ratio=decrease[fg_scaled];"
        f"[bg][fg_scaled]overlay=(W-w)/2:(H-h)/2[out]"
    )


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

        filter_args: list[str] = []
        map_args: list[str] = []
        if request.vertical_reframe:
            filter_graph = _build_reframe_filter()
            filter_args = ["-filter_complex", filter_graph]
            map_args = ["-map", "[out]", "-map", "0:a"]

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
            *filter_args,
            *map_args,
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

