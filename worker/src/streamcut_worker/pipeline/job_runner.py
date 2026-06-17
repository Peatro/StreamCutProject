from __future__ import annotations

from dataclasses import dataclass, field
import logging
from pathlib import Path
import threading
from typing import Callable, TypeVar

from streamcut_worker.analysis import CandidateAnalysisRequest, LoudnessProfile, SlidingWindowCandidateAnalysisService
from streamcut_worker.audio import AudioExtractionRequest, FfmpegAudioExtractionService
from streamcut_worker.export import ClipExportRequest, FfmpegClipExportService
from streamcut_worker.loudness import (
    FfmpegLoudnessDetectionService,
    LoudnessDetectionException,
    LoudnessDetectionRequest,
)
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

logger = logging.getLogger(__name__)


class WorkerJobRunnerError(RuntimeError):
    def __init__(self, failed_state: str, message: str) -> None:
        super().__init__(message)
        self.failed_state = failed_state


T = TypeVar("T")


@dataclass(slots=True)
class WorkerJobRunner:
    TRANSCRIPTION_PROGRESS_START = 48
    TRANSCRIPTION_PROGRESS_END = 67
    DETECTING_SILENCE_PROGRESS = 68
    ANALYZING_WINDOWS_PROGRESS = 84
    STAGE_HEARTBEAT_SEC = 30.0

    storage_root: Path
    source_materializer: SourceMaterializer
    audio_service: FfmpegAudioExtractionService
    transcription_service: FasterWhisperTranscriptionService | None
    silence_service: FfmpegSilenceDetectionService
    loudness_service: FfmpegLoudnessDetectionService
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

            def _on_yt_dlp_progress(download_percent: float) -> None:
                mapped = 18 + int(download_percent * (35 - 18) / 100)
                self._notify_progress(
                    on_progress,
                    "DOWNLOADING",
                    mapped,
                    f"Downloading source video ({download_percent:.0f}%)",
                )

            source_video_path = self._materialize_source(job, on_yt_dlp_progress=_on_yt_dlp_progress)
            return WorkerDownloadCompletionPayload(
                execution_id=job.execution_id,
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
        logger.info("stage_started jobId=%s stage=%s", job.job_id, "EXTRACTING_AUDIO")
        self._notify_progress(on_progress, "EXTRACTING_AUDIO", 36, "Processing worker is extracting the audio track")
        audio_result = self._extract_audio(source_video_path)
        logger.info("stage_started jobId=%s stage=%s", job.job_id, "TRANSCRIBING")
        self._notify_progress(on_progress, "TRANSCRIBING", 48, "Worker is transcribing the audio")
        transcription_result = self._transcribe(job, audio_result.audio_path, on_progress)
        logger.info("stage_started jobId=%s stage=%s", job.job_id, "DETECTING_SILENCE")
        self._notify_progress(
            on_progress,
            "DETECTING_SILENCE",
            self.DETECTING_SILENCE_PROGRESS,
            "Worker is detecting silence spans",
        )
        silence_result = self._detect_silence(source_video_path, on_progress)
        logger.info("stage_started jobId=%s stage=%s", job.job_id, "MEASURING_LOUDNESS")
        loudness_profile = self._measure_loudness(audio_result.audio_path, on_progress)
        logger.info("stage_started jobId=%s stage=%s", job.job_id, "ANALYZING_WINDOWS")
        self._notify_progress(
            on_progress,
            "ANALYZING_WINDOWS",
            self.ANALYZING_WINDOWS_PROGRESS,
            "Worker is scoring sliding analysis windows",
        )
        analysis_result = self._analyze(job, transcription_result, silence_result, loudness_profile, on_progress)
        self._notify_progress(on_progress, "GENERATING_CANDIDATES", 94, "Worker is assembling clip candidates")

        return WorkerProcessingPayload(
            execution_id=job.execution_id,
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
        source_video_path = self._materialize_existing_source(job)
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
            execution_id=job.execution_id,
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

    def _materialize_source(
        self,
        job: ClaimedJob,
        on_yt_dlp_progress: Callable[[float], None] | None = None,
    ) -> Path:
        try:
            return self.source_materializer.materialize(job, on_progress=on_yt_dlp_progress, allow_origin_download=True)
        except SourceMaterializationError as exc:
            raise WorkerJobRunnerError(exc.failed_state, str(exc)) from exc

    def _resolve_analysis_source(self, job: ClaimedJob) -> Path:
        return self._materialize_existing_source(job)

    def _materialize_existing_source(self, job: ClaimedJob) -> Path:
        try:
            return self.source_materializer.materialize(job, allow_origin_download=False)
        except SourceMaterializationError as exc:
            raise WorkerJobRunnerError(exc.failed_state, str(exc)) from exc

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

    def _transcribe(
        self,
        job: ClaimedJob,
        audio_path: Path,
        on_progress: Callable[[str, int, str], None] | None,
    ):
        if self.transcription_service is None:
            raise WorkerJobRunnerError("TRANSCRIBING", "No transcription service available for this worker role")

        heartbeat_stop = threading.Event()
        heartbeat_thread = self._start_transcription_heartbeat(job.job_id, on_progress, heartbeat_stop)
        last_logged_percent: int | None = None

        def handle_progress(processed_sec: float, total_sec: float) -> None:
            nonlocal last_logged_percent

            progress_percent = self._transcription_progress_percent(processed_sec, total_sec)
            if last_logged_percent is None or progress_percent - last_logged_percent >= 10:
                logger.info(
                    "transcription_progress jobId=%s percent=%s processedSec=%.0f totalSec=%.0f",
                    job.job_id,
                    progress_percent,
                    processed_sec,
                    total_sec,
                )
                last_logged_percent = progress_percent

            self._notify_progress(
                on_progress,
                "TRANSCRIBING",
                progress_percent,
                self._transcription_progress_message(processed_sec, total_sec),
            )

        logger.info("transcription_started jobId=%s audioPath=%s", job.job_id, audio_path)

        try:
            transcription_result = self.transcription_service.transcribe(
                TranscriptionRequest(
                    job_id=str(job.job_id),
                    audio_path=audio_path,
                ),
                on_progress=handle_progress,
            )
            logger.info(
                "transcription_completed jobId=%s durationSec=%.0f segmentCount=%s",
                job.job_id,
                transcription_result.duration_sec,
                len(transcription_result.transcript_segments),
            )
            return transcription_result
        except Exception as exc:
            raise WorkerJobRunnerError("TRANSCRIBING", str(exc)) from exc
        finally:
            heartbeat_stop.set()
            if heartbeat_thread is not None:
                heartbeat_thread.join()

    def _start_transcription_heartbeat(
        self,
        job_id: int,
        on_progress: Callable[[str, int, str], None] | None,
        stop_event: threading.Event,
    ) -> threading.Thread | None:
        return self._start_stage_heartbeat(
            on_progress,
            stop_event,
            status="TRANSCRIBING",
            progress_percent=self.TRANSCRIPTION_PROGRESS_START,
            message="Worker is still transcribing the audio",
            on_heartbeat=lambda: logger.debug("transcription_heartbeat jobId=%s", job_id),
        )

    def _start_stage_heartbeat(
        self,
        on_progress: Callable[[str, int, str], None] | None,
        stop_event: threading.Event,
        *,
        status: str,
        progress_percent: int,
        message: str,
        on_heartbeat: Callable[[], None] | None = None,
    ) -> threading.Thread | None:
        if on_progress is None:
            return None

        def heartbeat() -> None:
            while not stop_event.wait(self.STAGE_HEARTBEAT_SEC):
                if on_heartbeat is not None:
                    on_heartbeat()
                self._notify_progress(
                    on_progress,
                    status,
                    progress_percent,
                    message,
                )

        thread = threading.Thread(
            target=heartbeat,
            name=f"{status.lower().replace('_', '-')}-heartbeat",
            daemon=True,
        )
        thread.start()
        return thread

    @classmethod
    def _transcription_progress_percent(cls, processed_sec: float, total_sec: float) -> int:
        if total_sec <= 0:
            return cls.TRANSCRIPTION_PROGRESS_START

        bounded_ratio = min(max(processed_sec / total_sec, 0.0), 1.0)
        span = cls.TRANSCRIPTION_PROGRESS_END - cls.TRANSCRIPTION_PROGRESS_START
        return cls.TRANSCRIPTION_PROGRESS_START + int(round(bounded_ratio * span))

    @staticmethod
    def _transcription_progress_message(processed_sec: float, total_sec: float) -> str:
        if total_sec <= 0:
            return "Worker is transcribing the audio"
        return f"Worker is transcribing the audio ({processed_sec:.0f}s / {total_sec:.0f}s)"

    def _detect_silence(
        self,
        source_video_path: Path,
        on_progress: Callable[[str, int, str], None] | None,
    ):
        try:
            return self._run_with_stage_heartbeat(
                on_progress=on_progress,
                status="DETECTING_SILENCE",
                progress_percent=self.DETECTING_SILENCE_PROGRESS,
                message="Worker is still detecting silence spans",
                operation=lambda: self.silence_service.detect(
                    SilenceDetectionRequest(video_path=source_video_path)
                ),
            )
        except Exception as exc:
            raise WorkerJobRunnerError("DETECTING_SILENCE", str(exc)) from exc

    def _measure_loudness(
        self,
        audio_path: Path,
        on_progress: Callable[[str, int, str], None] | None,
    ) -> LoudnessProfile | None:
        """Run ffmpeg loudness measurement.  Returns None on any failure (graceful degradation)."""
        try:
            result = self.loudness_service.detect(
                LoudnessDetectionRequest(audio_path=audio_path)
            )
            if not result.samples:
                logger.warning("loudness_empty audioPath=%s — no samples parsed, falling back", audio_path)
                return None
            return LoudnessProfile(
                time_sec=tuple(s.time_sec for s in result.samples),
                rms_db=tuple(s.rms_db for s in result.samples),
            )
        except LoudnessDetectionException:
            logger.warning("loudness_failed audioPath=%s — falling back to prior scoring", audio_path, exc_info=True)
            return None
        except Exception:
            logger.warning("loudness_unexpected audioPath=%s — falling back to prior scoring", audio_path, exc_info=True)
            return None

    def _analyze(
        self,
        job: ClaimedJob,
        transcription_result,
        silence_result,
        loudness_profile: LoudnessProfile | None,
        on_progress: Callable[[str, int, str], None] | None,
    ):
        try:
            return self._run_with_stage_heartbeat(
                on_progress=on_progress,
                status="ANALYZING_WINDOWS",
                progress_percent=self.ANALYZING_WINDOWS_PROGRESS,
                message="Worker is still scoring sliding analysis windows",
                operation=lambda: self.analysis_service.analyze(
                    CandidateAnalysisRequest(
                        job_id=str(job.job_id),
                        transcript_segments=transcription_result.transcript_segments,
                        silence_segments=silence_result.silence_segments,
                        duration_sec=transcription_result.duration_sec,
                        emotion_keywords=self.emotion_keywords,
                        loudness_profile=loudness_profile,
                    )
                )
            )
        except Exception as exc:
            raise WorkerJobRunnerError("ANALYZING_WINDOWS", str(exc)) from exc

    def _run_with_stage_heartbeat(
        self,
        *,
        on_progress: Callable[[str, int, str], None] | None,
        status: str,
        progress_percent: int,
        message: str,
        operation: Callable[[], T],
    ) -> T:
        heartbeat_stop = threading.Event()
        heartbeat_thread = self._start_stage_heartbeat(
            on_progress,
            heartbeat_stop,
            status=status,
            progress_percent=progress_percent,
            message=message,
        )

        try:
            return operation()
        finally:
            heartbeat_stop.set()
            if heartbeat_thread is not None:
                heartbeat_thread.join()


def create_default_job_runner(
    *,
    storage_root: Path,
    emotion_keywords: tuple[str, ...] = (),
    load_transcription_model: bool = True,
    whisper_device: str = "cpu",
    whisper_compute_type: str = "int8",
) -> WorkerJobRunner:
    return WorkerJobRunner(
        storage_root=storage_root,
        source_materializer=SourceMaterializer(storage_root=storage_root),
        audio_service=FfmpegAudioExtractionService(),
        transcription_service=create_default_transcription_service(
            device=whisper_device,
            compute_type=whisper_compute_type,
        ) if load_transcription_model else None,
        silence_service=FfmpegSilenceDetectionService(),
        loudness_service=FfmpegLoudnessDetectionService(),
        analysis_service=SlidingWindowCandidateAnalysisService(),
        export_service=FfmpegClipExportService(storage_root),
        emotion_keywords=emotion_keywords,
    )
