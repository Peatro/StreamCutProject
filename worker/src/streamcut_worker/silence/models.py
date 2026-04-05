from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class ProcessExecutionResult:
    returncode: int
    stdout: str
    stderr: str


@dataclass(frozen=True, slots=True)
class SilenceDetectionRequest:
    video_path: Path
    noise_threshold_db: str = "-30dB"
    minimum_duration_sec: float = 0.5


@dataclass(frozen=True, slots=True)
class SilenceInterval:
    start_sec: float
    end_sec: float
    duration_sec: float


@dataclass(frozen=True, slots=True)
class SilenceDetectionResult:
    video_path: Path
    command: list[str]
    returncode: int
    stdout: str
    stderr: str
    silence_segments: list[SilenceInterval]
