from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.analysis import AnalysisWindow, CandidateAnalysisResult, ClipCandidate
from streamcut_worker.audio import AudioExtractionResult
from streamcut_worker.models import ClaimedJob
from streamcut_worker.pipeline import WorkerJobRunner, WorkerJobRunnerError
from streamcut_worker.services.source_materializer import SourceMaterializationError
from streamcut_worker.silence import SilenceDetectionResult, SilenceInterval
from streamcut_worker.transcription import TranscriptionResult, TranscriptSegment


class FakeSourceMaterializer:
    def __init__(self, resolved_path: Path | None = None, error: Exception | None = None) -> None:
        self.resolved_path = resolved_path
        self.error = error

    def materialize(self, job: ClaimedJob) -> Path:
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

    def transcribe(self, request):
        return self.result


class FakeSilenceService:
    def __init__(self, result: SilenceDetectionResult) -> None:
        self.result = result

    def detect(self, request):
        return self.result


class FakeAnalysisService:
    def __init__(self, result: CandidateAnalysisResult) -> None:
        self.result = result

    def analyze(self, request):
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
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(storage_root / "jobs" / "7" / "exports" / "candidate-1.mp4"),
            )

            result = runner.run(
                ClaimedJob(
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

            result = runner.run(
                ClaimedJob(
                    job_id=7,
                    processing_version=2,
                    task_type="ANALYZE",
                    source_type="FILE",
                    video_path=source_video_path,
                    source_url=None,
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

    def test_runner_wraps_source_failures_with_stage_context(self) -> None:
        with TemporaryDirectory() as temp_dir:
            runner = WorkerJobRunner(
                storage_root=Path(temp_dir),
                source_materializer=FakeSourceMaterializer(error=SourceMaterializationError("download failed")),
                audio_service=FakeAudioService(None),  # type: ignore[arg-type]
                transcription_service=FakeTranscriptionService(None),  # type: ignore[arg-type]
                silence_service=FakeSilenceService(None),  # type: ignore[arg-type]
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(Path("/tmp/out.mp4")),
            )

            with self.assertRaises(WorkerJobRunnerError) as ctx:
                runner.run(
                    ClaimedJob(
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

    def test_runner_rejects_analyze_jobs_without_video_path(self) -> None:
        with TemporaryDirectory() as temp_dir:
            runner = WorkerJobRunner(
                storage_root=Path(temp_dir),
                source_materializer=FakeSourceMaterializer(error=AssertionError("should not materialize")),
                audio_service=FakeAudioService(None),  # type: ignore[arg-type]
                transcription_service=FakeTranscriptionService(None),  # type: ignore[arg-type]
                silence_service=FakeSilenceService(None),  # type: ignore[arg-type]
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(Path("/tmp/out.mp4")),
            )

            with self.assertRaises(WorkerJobRunnerError) as ctx:
                runner.run(
                    ClaimedJob(
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
                analysis_service=FakeAnalysisService(None),  # type: ignore[arg-type]
                export_service=FakeExportService(artifact_path),
            )

            result = runner.run(
                ClaimedJob(
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
