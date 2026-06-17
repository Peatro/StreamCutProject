from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class LoudnessSample:
    """A single RMS loudness measurement at a point in time."""

    time_sec: float
    rms_db: float


@dataclass(frozen=True, slots=True)
class LoudnessDetectionRequest:
    audio_path: Path
    interval_sec: float = 1.0


@dataclass(frozen=True, slots=True)
class LoudnessDetectionResult:
    audio_path: Path
    command: list[str]
    returncode: int
    stderr: str
    samples: list[LoudnessSample]
