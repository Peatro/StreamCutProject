from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True, slots=True)
class ClaimedJob:
    job_id: int
    task_type: str
    source_type: str
    video_path: Path | None
    source_url: str | None
    candidate_id: int | None = None
    clip_start_sec: float | None = None
    clip_end_sec: float | None = None
    artifact_path: Path | None = None


@dataclass(frozen=True, slots=True)
class WorkerProcessingPayload:
    job_id: int
    duration_sec: int
    language: str | None
    video_path: str | None
    audio_path: str | None
    transcript_segments: list[dict[str, Any]]
    silence_segments: list[dict[str, Any]]
    analysis_windows: list[dict[str, Any]]
    clip_candidates: list[dict[str, Any]]

    def to_payload(self) -> dict[str, Any]:
        return {
            "jobId": self.job_id,
            "durationSec": self.duration_sec,
            "language": self.language,
            "videoPath": self.video_path,
            "audioPath": self.audio_path,
            "transcriptSegments": self.transcript_segments,
            "silenceSegments": self.silence_segments,
            "analysisWindows": self.analysis_windows,
            "clipCandidates": self.clip_candidates,
        }


@dataclass(frozen=True, slots=True)
class WorkerExportCompletionPayload:
    job_id: int
    candidate_id: int
    artifact_path: str

    def to_payload(self) -> dict[str, Any]:
        return {
            "jobId": self.job_id,
            "candidateId": self.candidate_id,
            "artifactPath": self.artifact_path,
        }


@dataclass(frozen=True, slots=True)
class WorkerFailurePayload:
    job_id: int
    failed_state: str
    message: str

    def to_payload(self) -> dict[str, Any]:
        return {
            "jobId": self.job_id,
            "failedState": self.failed_state,
            "message": self.message,
        }
