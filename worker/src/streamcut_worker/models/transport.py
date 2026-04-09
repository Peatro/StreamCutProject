from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True, slots=True)
class ClaimedJob:
    execution_id: int
    job_id: int
    processing_version: int
    task_type: str
    source_type: str
    video_path: Path | None
    source_url: str | None
    candidate_id: int | None = None
    clip_start_sec: float | None = None
    clip_end_sec: float | None = None
    artifact_path: Path | None = None


@dataclass(frozen=True, slots=True)
class WorkerDownloadCompletionPayload:
    execution_id: int
    job_id: int
    worker_id: str
    processing_version: int
    video_path: str

    def to_payload(self) -> dict[str, Any]:
        return {
            "executionId": self.execution_id,
            "jobId": self.job_id,
            "workerId": self.worker_id,
            "processingVersion": self.processing_version,
            "videoPath": self.video_path,
        }


@dataclass(frozen=True, slots=True)
class WorkerProcessingPayload:
    execution_id: int
    job_id: int
    worker_id: str
    processing_version: int
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
            "executionId": self.execution_id,
            "jobId": self.job_id,
            "workerId": self.worker_id,
            "processingVersion": self.processing_version,
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
    execution_id: int
    job_id: int
    worker_id: str
    processing_version: int
    candidate_id: int
    artifact_path: str

    def to_payload(self) -> dict[str, Any]:
        return {
            "executionId": self.execution_id,
            "jobId": self.job_id,
            "workerId": self.worker_id,
            "processingVersion": self.processing_version,
            "candidateId": self.candidate_id,
            "artifactPath": self.artifact_path,
        }


@dataclass(frozen=True, slots=True)
class WorkerFailurePayload:
    execution_id: int
    job_id: int
    worker_id: str
    processing_version: int
    failed_state: str
    message: str

    def to_payload(self) -> dict[str, Any]:
        return {
            "executionId": self.execution_id,
            "jobId": self.job_id,
            "workerId": self.worker_id,
            "processingVersion": self.processing_version,
            "failedState": self.failed_state,
            "message": self.message,
        }


@dataclass(frozen=True, slots=True)
class WorkerProgressPayload:
    execution_id: int
    job_id: int
    worker_id: str
    processing_version: int
    status: str
    progress_percent: int
    message: str

    def to_payload(self) -> dict[str, Any]:
        return {
            "executionId": self.execution_id,
            "jobId": self.job_id,
            "workerId": self.worker_id,
            "processingVersion": self.processing_version,
            "status": self.status,
            "progressPercent": self.progress_percent,
            "message": self.message,
        }
