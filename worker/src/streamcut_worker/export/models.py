from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class ProcessExecutionResult:
    returncode: int
    stdout: str
    stderr: str


@dataclass(frozen=True, slots=True)
class ClipExportRequest:
    job_id: str
    candidate_id: str
    source_video_path: Path
    start_sec: float
    end_sec: float
    vertical_reframe: bool = False


@dataclass(frozen=True, slots=True)
class ClipExportResult:
    job_id: str
    candidate_id: str
    source_video_path: Path
    artifact_path: Path
    start_sec: float
    end_sec: float
    duration_sec: float
    command: list[str]
    returncode: int
    stdout: str
    stderr: str
    status: str = "COMPLETED"

