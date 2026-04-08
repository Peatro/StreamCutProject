from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from streamcut_worker.analysis import CandidateAnalysisRequest, SlidingWindowCandidateAnalysisService
from streamcut_worker.audio import AudioExtractionRequest, FfmpegAudioExtractionService
from streamcut_worker.export import ClipExportRequest, FfmpegClipExportService
from streamcut_worker.models import (
    ClaimedJob,
    WorkerDownloadCompletionPayload,
    WorkerExportCompletionPayload,
    WorkerProcessingPayload,
)
from streamcut_worker.services.source_materializer import SourceMaterializationError, SourceMaterializer
from streamcut_worker.silence import FfmpegSilenceDetectionService, SilenceDetectionRequest
from streamcut_worker.transcription import (
    FasterWhisperTranscriptionService,
    TranscriptionRequest,
    create_default_transcription_service,
)


class WorkerJobRunnerError(RuntimeError):
    def __init__(self, failed_state: str, message: str) -> None:
        super().__init__(message)
        self.failed_state = failed_state


@dataclass(slots=True)
class WorkerJobRunner:
    storage_root: Path
    source_materializer: SourceMaterializer
    audio_service: FfmpegAudioExtractionService
    transcription_service: FasterWhisperTranscriptionService
    silence_service: FfmpegSilenceDetectionService
    analysis_service: SlidingWindowCandidateAnalysisService
    export_service: FfmpegClipExportService
    emotion_keywords: tuple[str, ...] = field(default_factory=tuple)

    def run(
        self,
        job: ClaimedJob,
        on_progress: Callable[[str, int, str], None] | None = None,
        *,
        worker_id: str,
    ) -> WorkerDownloadCompletionPayload | WorkerProcessingPayload | WorkerExportCompletionPayload:
        if job.task_type == "DOWNLOAD":
            self._notify_progress(on_progress, "DOWNLOADING", 18, "Download worker is materializing the source video")
            source_video_path = self._materialize_source(job)
            return WorkerDownloadCompletionPayload(
                job_id=job.job_id,
                worker_id=worker_id,
                processing_version=job.processing_version,
                video_path=str(source_video_path),
            )

        if job.task_type == "EXPORT":
            if on_progress is not None:
                on_progress("EXPORTING_CLIP", 92, "Worker is exporting the approved clip")
            return self._export(job, worker_id)

        source_video_path = self._resolve_analysis_source(job)
        self._notify_progress(on_progress, "EXTRACTING_AUDIO", 36, "Processing worker is extracting the audio track")
        audio_result = self._extract_audio(source_video_path)
        self._notify_progress(on_progress, "TRANSCRIBING", 48, "Worker is transcribing the audio")
        transcription_result = self._transcribe(job, audio_result.audio_path)
        self._notify_progress(on_progress, "DETECTING_SILENCE", 68, "Worker is detecting silence spans")
        silence_result = self._detect_silence(source_video_path)
        self._notify_progress(on_progress, "ANALYZING_WINDOWS", 84, "Worker is scoring sliding analysis windows")
        analysis_result = self._analyze(job, transcription_result, silence_result)
        self._notify_progress(on_progress, "GENERATING_CANDIDATES", 94, "Worker is assembling clip candidates")

        return WorkerProcessingPayload(
            job_id=job.job_id,
            worker_id=worker_id,
            processing_version=job.processing_version,
            duration_sec=max(0, int(round(transcription_result.duration_sec))),
            language=transcription_result.language,
            video_path=str(source_video_path),
            audio_path=str(audio_result.audio_path),
            transcript_segments=[
                {
                    "startSec": segment.start_sec,
                    "endSec": segment.end_sec,
                    "text": segment.text,
                    "wordCount": segment.word_count,
                }
                for segment in transcription_result.transcript_segments
            ],
            silence_segments=[
                {
                    "startSec": segment.start_sec,
                    "endSec": segment.end_sec,
                    "durationSec": segment.duration_sec,
                }
                for segment in silence_result.silence_segments
            ],
            analysis_windows=[
                {
                    "startSec": window.start_sec,
                    "endSec": window.end_sec,
                    "speechDensity": window.speech_density,
                    "silenceRatio": window.silence_ratio,
                    "emotionHits": window.emotion_hits,
                    "continuityScore": window.continuity_score,
                    "totalScore": window.total_score,
                }
                for window in analysis_result.analysis_windows
            ],
            clip_candidates=[
                {
                    "startSec": candidate.start_sec,
                    "endSec": candidate.end_sec,
                    "score": candidate.score,
                    "transcriptExcerpt": candidate.transcript_excerpt,
                }
                for candidate in analysis_result.clip_candidates
            ],
        )

    def _export(self, job: ClaimedJob, worker_id: str) -> WorkerExportCompletionPayload:
        source_video_path = self._materialize_source(job)
        if job.candidate_id is None or job.clip_start_sec is None or job.clip_end_sec is None:
            raise WorkerJobRunnerError("EXPORTING_CLIP", f"Export job {job.job_id} is missing clip boundaries")

        try:
            export_result = self.export_service.export(
                ClipExportRequest(
                    job_id=str(job.job_id),
                    candidate_id=str(job.candidate_id),
                    source_video_path=source_video_path,
                    start_sec=job.clip_start_sec,
                    end_sec=job.clip_end_sec,
                )
            )
        except Exception as exc:
            raise WorkerJobRunnerError("EXPORTING_CLIP", str(exc)) from exc

        return WorkerExportCompletionPayload(
            job_id=job.job_id,
            worker_id=worker_id,
            processing_version=job.processing_version,
            candidate_id=job.candidate_id,
            artifact_path=str(export_result.artifact_path),
        )

    @staticmethod
    def _notify_progress(
        on_progress: Callable[[str, int, str], None] | None,
        status: str,
        progress_percent: int,
        message: str,
    ) -> None:
        if on_progress is None:
            return
        on_progress(status, progress_percent, message)

    def _materialize_source(self, job: ClaimedJob) -> Path:
        try:
            return self.source_materializer.materialize(job)
        except SourceMaterializationError as exc:
            raise WorkerJobRunnerError(exc.failed_state, str(exc)) from exc

    def _resolve_analysis_source(self, job: ClaimedJob) -> Path:
        if job.video_path is None:
            raise WorkerJobRunnerError("EXTRACTING_AUDIO", f"Analyze job {job.job_id} is missing videoPath")
        return job.video_path

    def _extract_audio(self, source_video_path: Path):
        try:
            return self.audio_service.extract(
                AudioExtractionRequest(
                    video_path=source_video_path,
                    output_dir=self.storage_root / "jobs" / source_video_path.parents[1].name / "audio",
                )
            )
        except Exception as exc:
            raise WorkerJobRunnerError("EXTRACTING_AUDIO", str(exc)) from exc

    def _transcribe(self, job: ClaimedJob, audio_path: Path):
        try:
            return self.transcription_service.transcribe(
                TranscriptionRequest(
                    job_id=str(job.job_id),
                    audio_path=audio_path,
                )
            )
        except Exception as exc:
            raise WorkerJobRunnerError("TRANSCRIBING", str(exc)) from exc

    def _detect_silence(self, source_video_path: Path):
        try:
            return self.silence_service.detect(
                SilenceDetectionRequest(video_path=source_video_path)
            )
        except Exception as exc:
            raise WorkerJobRunnerError("DETECTING_SILENCE", str(exc)) from exc

    def _analyze(self, job: ClaimedJob, transcription_result, silence_result):
        try:
            return self.analysis_service.analyze(
                CandidateAnalysisRequest(
                    job_id=str(job.job_id),
                    transcript_segments=transcription_result.transcript_segments,
                    silence_segments=silence_result.silence_segments,
                    duration_sec=transcription_result.duration_sec,
                    emotion_keywords=self.emotion_keywords,
                )
            )
        except Exception as exc:
            raise WorkerJobRunnerError("ANALYZING_WINDOWS", str(exc)) from exc


def create_default_job_runner(
    *,
    storage_root: Path,
    emotion_keywords: tuple[str, ...] = (),
) -> WorkerJobRunner:
    return WorkerJobRunner(
        storage_root=storage_root,
        source_materializer=SourceMaterializer(storage_root=storage_root),
        audio_service=FfmpegAudioExtractionService(),
        transcription_service=create_default_transcription_service(),
        silence_service=FfmpegSilenceDetectionService(),
        analysis_service=SlidingWindowCandidateAnalysisService(),
        export_service=FfmpegClipExportService(storage_root),
        emotion_keywords=emotion_keywords,
    )
