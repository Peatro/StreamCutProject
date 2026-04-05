from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class ProcessExecutionResult:
    returncode: int
    stdout: str
    stderr: str


@dataclass(frozen=True, slots=True)
class AudioExtractionRequest:
    video_path: Path
    output_dir: Path
    output_filename: str | None = None


@dataclass(frozen=True, slots=True)
class AudioExtractionResult:
    video_path: Path
    audio_path: Path
    command: list[str]
    returncode: int
    stdout: str
    stderr: str

