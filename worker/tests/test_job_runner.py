from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import time
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.analysis import AnalysisWindow, CandidateAnalysisResult, ClipCandidate
from streamcut_worker.audio import AudioExtractionResult
from streamcut_worker.loudness import LoudnessDetectionResult, LoudnessSample
from streamcut_worker.models import ClaimedJob
from streamcut_worker.pipeline import WorkerJobRunner, WorkerJobRunnerError
from streamcut_worker.pipeline.job_runner import _temporal_nms
from streamcut_worker.services.source_materializer import SourceMaterializationError
from streamcut_worker.silence import SilenceDetectionResult, SilenceInterval
from streamcut_worker.transcription import TranscriptionResult, TranscriptSegment


class FakeSourceMaterializer:
    def __init__(self, resolved_path: Path | None = None, error: Exception | None = None) -> None:
        self.resolved_path = resolved_path
        self.error = error

    def materialize(self, job: ClaimedJob, on_progress=None, *, allow_origin_download: bool = True) -> Path:
        if self.error is not None:
            raise self.error
        assert self.resolved_path is not None
        return self.resolved_path


class FakeAudioService:
    def __init__(self, result: AudioExtractionResult) -> None:
        self.result = result

    def extract(self, request):
        return self.result


class FakeTranscriptionService:
    def __init__(self, result: TranscriptionResult) -> None:
        self.result = result
        self.progress_updates: list[tuple[float, float]] = []

    def transcribe(self, request, on_progress=None):
        if on_progress is not None:
            on_progress(30.0, 120.0)
            on_progress(90.0, 120.0)
        return self.result


class FakeSilenceService:
    def __init__(self, result: SilenceDetectionResult) -> None:
        self.result = result

    def detect(self, request):
        return self.result


class SlowFakeSilenceService(FakeSilenceService):
    def __init__(self, result: SilenceDetectionResult, delay_sec: float) -> None:
        super().__init__(result)
        self.delay_sec = delay_sec

    def detect(self, request):
        time.sleep(self.delay_sec)
        return self.result


class FakeAnalysisService:
    def __init__(self, result: CandidateAnalysisResult) -> None:
        self.result = result

    def analyze(self, request):
        return self.result


class SlowFakeAnalysisService(FakeAnalysisService):
    def __init__(self, result: CandidateAnalysisResult, delay_sec: float) -> None:
        super().__init__(result)
        self.delay_sec = delay_sec

    def analyze(self, request):
        time.sleep(self.delay_sec)
        return self.result


class FakeLoudnessService:
    def __init__(self, result: LoudnessDetectionResult | None = None) -> None:
        self.result = result or LoudnessDetectionResult(
            audio_path=Path("/dev/null"),
            command=["ffmpeg"],
            returncode=0,
            stderr="",
            samples=[],
        )

    def detect(self, request):
        return self.result


class FakeExportResult:
    def __init__(self, artifact_path: Path) -> None:
        self.artifact_path = artifact_path


class FakeExportService:
    def __init__(self, artifact_path: Path) -> None:
        self.artifact_path = artifact_path

    def export(self, request):
        return FakeExportResult(self.artifact_path)


class WorkerJobRunnerTests(unittest.TestCase):
    def test_runner_builds_download_completion_payload(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            source_video_path = storage_root / "jobs" / "7" / "source" / "video.mp4"
            source_video_path.parent.mkdir(parents=True, exist_ok=True)
            source_video_path.write_bytes(b"video")

            runner = WorkerJobRunner(
                storage_root=storage_root,
                source_materializer=FakeSourceMaterializer(resolved_path=source_video_path),
                audio_service=FakeAudioService(None),  # type: ignore[arg-type]
                transcription_service=FakeTranscriptionService(None),  # type: ignore[arg-type]
                silence_service=FakeSilenceService(None),  # type: ignore[arg-type]
                loudness_service=FakeLoudnessService(),
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(storage_root / "jobs" / "7" / "exports" / "candidate-1.mp4"),
            )

            result = runner.run(
                ClaimedJob(
                    execution_id=201,
                    job_id=7,
                    processing_version=2,
                    task_type="DOWNLOAD",
                    source_type="URL",
                    video_path=None,
                    source_url="https://example.com/video.mp4",
                ),
                worker_id="download-worker-1",
            )

        self.assertEqual(result.job_id, 7)
        self.assertEqual(result.worker_id, "download-worker-1")
        self.assertEqual(result.processing_version, 2)
        self.assertEqual(result.video_path, str(source_video_path))

    def test_runner_builds_success_payload_from_processing_services(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            source_video_path = storage_root / "jobs" / "7" / "source" / "video.mp4"
            audio_path = storage_root / "jobs" / "7" / "audio" / "video.wav"
            source_video_path.parent.mkdir(parents=True, exist_ok=True)
            source_video_path.write_bytes(b"video")

            runner = WorkerJobRunner(
                storage_root=storage_root,
                source_materializer=FakeSourceMaterializer(resolved_path=source_video_path),
                audio_service=FakeAudioService(
                    AudioExtractionResult(source_video_path, audio_path, ["ffmpeg"], 0, "", "")
                ),
                transcription_service=FakeTranscriptionService(
                    TranscriptionResult(
                        job_id="7",
                        audio_path=audio_path,
                        duration_sec=122.4,
                        language="en",
                        language_probability=0.99,
                        transcript_segments=[TranscriptSegment(0.0, 2.0, "hello there", 2)],
                    )
                ),
                silence_service=FakeSilenceService(
                    SilenceDetectionResult(
                        video_path=source_video_path,
                        command=["ffmpeg"],
                        returncode=0,
                        stdout="",
                        stderr="",
                        silence_segments=[SilenceInterval(2.0, 3.0, 1.0)],
                    )
                ),
                loudness_service=FakeLoudnessService(),
                analysis_service=FakeAnalysisService(
                    CandidateAnalysisResult(
                        job_id="7",
                        duration_sec=122.4,
                        analysis_windows=[AnalysisWindow(0.0, 30.0, 0.5, 0.1, 0, 0.8, 0.9)],
                        clip_candidates=[ClipCandidate(5.0, 15.0, 0.9, "hello there")],
                    )
                ),
                export_service=FakeExportService(storage_root / "jobs" / "7" / "exports" / "candidate-1.mp4"),
            )
            progress_updates: list[tuple[str, int, str]] = []

            result = runner.run(
                ClaimedJob(
                    execution_id=202,
                    job_id=7,
                    processing_version=2,
                    task_type="ANALYZE",
                    source_type="FILE",
                    video_path=source_video_path,
                    source_url=None,
                ),
                on_progress=lambda status, progress_percent, message: progress_updates.append(
                    (status, progress_percent, message)
                ),
                worker_id="processing-worker-1",
            )

        self.assertEqual(result.job_id, 7)
        self.assertEqual(result.worker_id, "processing-worker-1")
        self.assertEqual(result.processing_version, 2)
        self.assertEqual(result.duration_sec, 122)
        self.assertEqual(result.language, "en")
        self.assertEqual(result.video_path, str(source_video_path))
        self.assertEqual(result.audio_path, str(audio_path))
        self.assertEqual(result.transcript_segments[0]["wordCount"], 2)
        self.assertEqual(result.silence_segments[0]["durationSec"], 1.0)
        self.assertEqual(result.analysis_windows[0]["totalScore"], 0.9)
        self.assertEqual(result.clip_candidates[0]["transcriptExcerpt"], "hello there")
        self.assertIn(("TRANSCRIBING", 53, "Worker is transcribing the audio (30s / 120s)"), progress_updates)
        self.assertIn(("TRANSCRIBING", 62, "Worker is transcribing the audio (90s / 120s)"), progress_updates)

    def test_runner_emits_heartbeat_during_long_silence_and_analysis_stages(self) -> None:
        class FastHeartbeatRunner(WorkerJobRunner):
            STAGE_HEARTBEAT_SEC = 0.01

        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            source_video_path = storage_root / "jobs" / "7" / "source" / "video.mp4"
            audio_path = storage_root / "jobs" / "7" / "audio" / "video.wav"
            source_video_path.parent.mkdir(parents=True, exist_ok=True)
            source_video_path.write_bytes(b"video")

            silence_result = SilenceDetectionResult(
                video_path=source_video_path,
                command=["ffmpeg"],
                returncode=0,
                stdout="",
                stderr="",
                silence_segments=[SilenceInterval(2.0, 3.0, 1.0)],
            )
            analysis_result = CandidateAnalysisResult(
                job_id="7",
                duration_sec=122.4,
                analysis_windows=[AnalysisWindow(0.0, 30.0, 0.5, 0.1, 0, 0.8, 0.9)],
                clip_candidates=[ClipCandidate(5.0, 15.0, 0.9, "hello there")],
            )
            runner = FastHeartbeatRunner(
                storage_root=storage_root,
                source_materializer=FakeSourceMaterializer(resolved_path=source_video_path),
                audio_service=FakeAudioService(
                    AudioExtractionResult(source_video_path, audio_path, ["ffmpeg"], 0, "", "")
                ),
                transcription_service=FakeTranscriptionService(
                    TranscriptionResult(
                        job_id="7",
                        audio_path=audio_path,
                        duration_sec=122.4,
                        language="en",
                        language_probability=0.99,
                        transcript_segments=[TranscriptSegment(0.0, 2.0, "hello there", 2)],
                    )
                ),
                silence_service=SlowFakeSilenceService(silence_result, delay_sec=0.05),
                loudness_service=FakeLoudnessService(),
                analysis_service=SlowFakeAnalysisService(analysis_result, delay_sec=0.05),
                export_service=FakeExportService(storage_root / "jobs" / "7" / "exports" / "candidate-1.mp4"),
            )
            progress_updates: list[tuple[str, int, str]] = []

            runner.run(
                ClaimedJob(
                    execution_id=206,
                    job_id=7,
                    processing_version=2,
                    task_type="ANALYZE",
                    source_type="FILE",
                    video_path=source_video_path,
                    source_url=None,
                ),
                on_progress=lambda status, progress_percent, message: progress_updates.append(
                    (status, progress_percent, message)
                ),
                worker_id="processing-worker-1",
            )

        self.assertIn(
            ("DETECTING_SILENCE", 68, "Worker is still detecting silence spans"),
            progress_updates,
        )
        self.assertIn(
            ("ANALYZING_WINDOWS", 84, "Worker is still scoring sliding analysis windows"),
            progress_updates,
        )

    def test_runner_wraps_source_failures_with_stage_context(self) -> None:
        with TemporaryDirectory() as temp_dir:
            runner = WorkerJobRunner(
                storage_root=Path(temp_dir),
                source_materializer=FakeSourceMaterializer(error=SourceMaterializationError("download failed")),
                audio_service=FakeAudioService(None),  # type: ignore[arg-type]
                transcription_service=FakeTranscriptionService(None),  # type: ignore[arg-type]
                silence_service=FakeSilenceService(None),  # type: ignore[arg-type]
                loudness_service=FakeLoudnessService(),
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(Path("/tmp/out.mp4")),
            )

            with self.assertRaises(WorkerJobRunnerError) as ctx:
                runner.run(
                    ClaimedJob(
                        execution_id=203,
                        job_id=8,
                        processing_version=1,
                        task_type="DOWNLOAD",
                        source_type="URL",
                        video_path=None,
                        source_url="https://example.com/video.mp4",
                    ),
                    worker_id="download-worker-1",
                )

        self.assertEqual(ctx.exception.failed_state, "DOWNLOADING")
        self.assertIn("download failed", str(ctx.exception))

    def test_runner_rejects_analyze_jobs_without_durable_source_access(self) -> None:
        with TemporaryDirectory() as temp_dir:
            runner = WorkerJobRunner(
                storage_root=Path(temp_dir),
                source_materializer=FakeSourceMaterializer(
                    error=SourceMaterializationError(
                        "Job 8 is missing a durable source download URL",
                        failed_state="EXTRACTING_AUDIO",
                    )
                ),
                audio_service=FakeAudioService(None),  # type: ignore[arg-type]
                transcription_service=FakeTranscriptionService(None),  # type: ignore[arg-type]
                silence_service=FakeSilenceService(None),  # type: ignore[arg-type]
                loudness_service=FakeLoudnessService(),
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(Path("/tmp/out.mp4")),
            )

            with self.assertRaises(WorkerJobRunnerError) as ctx:
                runner.run(
                    ClaimedJob(
                        execution_id=204,
                        job_id=8,
                        processing_version=1,
                        task_type="ANALYZE",
                        source_type="URL",
                        video_path=None,
                        source_url="https://example.com/video.mp4",
                    ),
                    worker_id="processing-worker-1",
                )

        self.assertEqual(ctx.exception.failed_state, "EXTRACTING_AUDIO")

    def test_runner_handles_export_jobs(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            source_video_path = storage_root / "jobs" / "9" / "source" / "video.mp4"
            source_video_path.parent.mkdir(parents=True, exist_ok=True)
            source_video_path.write_bytes(b"video")
            artifact_path = storage_root / "jobs" / "9" / "exports" / "candidate-3.mp4"

            runner = WorkerJobRunner(
                storage_root=storage_root,
                source_materializer=FakeSourceMaterializer(resolved_path=source_video_path),
                audio_service=FakeAudioService(None),  # type: ignore[arg-type]
                transcription_service=FakeTranscriptionService(None),  # type: ignore[arg-type]
                silence_service=FakeSilenceService(None),  # type: ignore[arg-type]
                loudness_service=FakeLoudnessService(),
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(artifact_path),
            )

            result = runner.run(
                ClaimedJob(
                    execution_id=205,
                    job_id=9,
                    processing_version=5,
                    task_type="EXPORT",
                    source_type="FILE",
                    video_path=source_video_path,
                    source_url=None,
                    candidate_id=3,
                    clip_start_sec=5.0,
                    clip_end_sec=12.0,
                    artifact_path=artifact_path,
                ),
                worker_id="processing-worker-1",
            )

        self.assertEqual(result.job_id, 9)
        self.assertEqual(result.worker_id, "processing-worker-1")
        self.assertEqual(result.processing_version, 5)
        self.assertEqual(result.candidate_id, 3)
        self.assertEqual(result.artifact_path, str(artifact_path))


class TemporalNmsTests(unittest.TestCase):
    """Greedy time-domain NMS in the candidate gate (lossless dedup, no top-N cut)."""

    def test_suppresses_near_duplicates_keeps_higher_score(self) -> None:
        # Two detections ~20s apart (same moment) + one far away. Window 45s
        # collapses the near pair to the higher-scoring one; the distant one survives.
        cands = [
            ClipCandidate(100.0, 110.0, 0.7, "a"),   # near, lower score -> dropped
            ClipCandidate(120.0, 130.0, 0.9, "b"),   # near, higher score -> kept
            ClipCandidate(900.0, 910.0, 0.6, "c"),   # far -> kept
        ]
        kept = _temporal_nms(cands, window_sec=45.0)
        self.assertEqual([c.transcript_excerpt for c in kept], ["b", "c"])

    def test_no_score_cut_keeps_all_distinct_moments(self) -> None:
        # 50 well-separated low-and-high score candidates: none suppressed, all kept
        # (proves we do not silently drop true highlights via a top-N-by-score cap).
        cands = [ClipCandidate(i * 100.0, i * 100.0 + 5, 0.5 + (i % 5) * 0.1, f"m{i}") for i in range(50)]
        kept = _temporal_nms(cands, window_sec=45.0)
        self.assertEqual(len(kept), 50)

    def test_window_zero_is_noop(self) -> None:
        cands = [ClipCandidate(100.0, 110.0, 0.7, "a"), ClipCandidate(105.0, 115.0, 0.9, "b")]
        kept = _temporal_nms(cands, window_sec=0.0)
        self.assertEqual(len(kept), 2)
