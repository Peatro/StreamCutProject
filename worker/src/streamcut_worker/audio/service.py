from __future__ import annotations

from pathlib import Path

from .exceptions import AudioExtractionException
from .models import AudioExtractionRequest, AudioExtractionResult
from .process import ProcessRunner, SubprocessProcessRunner


class FfmpegAudioExtractionService:
    def __init__(self, runner: ProcessRunner | None = None) -> None:
        self._runner = runner or SubprocessProcessRunner()

    def extract(self, request: AudioExtractionRequest) -> AudioExtractionResult:
        if not request.video_path.exists():
            raise AudioExtractionException(
                f"Input video does not exist: {request.video_path}",
            )

        output_path = self._resolve_output_path(request)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        command = [
            "ffmpeg",
            "-y",
            "-i",
            str(request.video_path),
            "-vn",
            "-ac",
            "1",
            "-ar",
            "16000",
            "-c:a",
            "pcm_s16le",
            str(output_path),
        ]

        result = self._runner.run(command)
        if result.returncode != 0:
            raise AudioExtractionException(
                "ffmpeg failed while extracting audio",
                command=list(command),
                returncode=result.returncode,
                stderr=result.stderr.strip() or None,
            )

        return AudioExtractionResult(
            video_path=request.video_path,
            audio_path=output_path,
            command=list(command),
            returncode=result.returncode,
            stdout=result.stdout,
            stderr=result.stderr,
        )

    def _resolve_output_path(self, request: AudioExtractionRequest) -> Path:
        output_name = request.output_filename or f"{request.video_path.stem}.wav"
        if not output_name.lower().endswith(".wav"):
            output_name = f"{Path(output_name).stem}.wav"
        return request.output_dir / output_name

