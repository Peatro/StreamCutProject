from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.models import (
    ClaimedJob,
    WorkerDownloadCompletionPayload,
    WorkerExportCompletionPayload,
    WorkerFailurePayload,
    WorkerProcessingPayload,
)
from streamcut_worker.pipeline import WorkerJobRunnerError, WorkerPollingLoop
from streamcut_worker.services.backend_client import BackendTransportError


class FakeBackendClient:
    def __init__(self, claimed_job=None, result_error=None) -> None:
        self.claimed_job = claimed_job
        self.claim_calls = 0
        self.claim_roles = []
        self.results = []
        self.download_results = []
        self.export_results = []
        self.failures = []
        self.result_error = result_error

    def claim_next_job(self, worker_id: str, worker_role: str):
        self.claim_calls += 1
        self.claim_roles.append((worker_id, worker_role))
        claimed_job = self.claimed_job
        self.claimed_job = None
        return claimed_job

    def submit_result(self, payload: WorkerProcessingPayload):
        if self.result_error is not None:
            raise self.result_error
        self.results.append(payload)
        return {"status": "READY_FOR_REVIEW"}

    def submit_download_result(self, payload: WorkerDownloadCompletionPayload):
        self.download_results.append(payload)
        return {"status": "QUEUED_FOR_PROCESSING"}

    def submit_failure(self, payload: WorkerFailurePayload):
        self.failures.append(payload)
        return {"status": "FAILED"}

    def submit_export_result(self, payload: WorkerExportCompletionPayload):
        self.export_results.append(payload)
        return {"status": "COMPLETED"}

    def submit_progress(self, payload):
        return {"status": payload.status}


class FakeJobRunner:
    def __init__(self, result=None, error: Exception | None = None) -> None:
        self.result = result
        self.error = error

    def run(self, job: ClaimedJob, on_progress=None, *, worker_id: str):
        if self.error is not None:
            raise self.error
        return self.result


class WorkerPollingLoopTests(unittest.TestCase):
    def test_polling_loop_claims_processing_job_and_submits_result(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=101,
                job_id=7,
                processing_version=2,
                task_type="ANALYZE",
                source_type="FILE",
                video_path=Path("/tmp/video.mp4"),
                source_url=None,
            )
        )
        runner = FakeJobRunner(
            result=WorkerProcessingPayload(
                execution_id=101,
                job_id=7,
                worker_id="processing-worker-1",
                processing_version=2,
                duration_sec=120,
                language="en",
                video_path="/tmp/video.mp4",
                audio_path="/tmp/audio.wav",
                transcript_segments=[],
                silence_segments=[],
                analysis_windows=[],
                clip_candidates=[],
            )
        )
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="processing-worker-1",
            worker_role="processing",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(backend.claim_calls, 1)
        self.assertEqual(backend.claim_roles, [("processing-worker-1", "processing")])
        self.assertEqual(len(backend.results), 1)
        self.assertEqual(backend.results[0].job_id, 7)

    def test_polling_loop_submits_download_result(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=102,
                job_id=8,
                processing_version=3,
                task_type="DOWNLOAD",
                source_type="URL",
                video_path=None,
                source_url="https://example.com/video.mp4",
            )
        )
        runner = FakeJobRunner(
            result=WorkerDownloadCompletionPayload(
                execution_id=102,
                job_id=8,
                worker_id="download-worker-1",
                processing_version=3,
                video_path="/tmp/video.mp4",
            )
        )
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="download-worker-1",
            worker_role="download",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(backend.claim_roles, [("download-worker-1", "download")])
        self.assertEqual(len(backend.download_results), 1)
        self.assertEqual(backend.download_results[0].job_id, 8)

    def test_polling_loop_reports_failures(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=103,
                job_id=10,
                processing_version=1,
                task_type="DOWNLOAD",
                source_type="URL",
                video_path=None,
                source_url="https://example.com/video.mp4",
            )
        )
        runner = FakeJobRunner(error=WorkerJobRunnerError("DOWNLOADING", "download failed"))
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="download-worker-1",
            worker_role="download",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(len(backend.failures), 1)
        self.assertEqual(backend.failures[0].job_id, 10)
        self.assertEqual(backend.failures[0].failed_state, "DOWNLOADING")

    def test_polling_loop_reports_unexpected_runner_failures(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=104,
                job_id=11,
                processing_version=1,
                task_type="ANALYZE",
                source_type="FILE",
                video_path=Path("/tmp/video.mp4"),
                source_url=None,
            )
        )
        runner = FakeJobRunner(error=RuntimeError("boom"))
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="processing-worker-1",
            worker_role="processing",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(len(backend.failures), 1)
        self.assertEqual(backend.failures[0].job_id, 11)
        self.assertEqual(backend.failures[0].failed_state, "WORKER_INTERNAL")
        self.assertIn("Unexpected worker failure: boom", backend.failures[0].message)

    def test_polling_loop_preserves_backend_transport_errors(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=105,
                job_id=12,
                processing_version=2,
                task_type="ANALYZE",
                source_type="FILE",
                video_path=Path("/tmp/video.mp4"),
                source_url=None,
            ),
            result_error=BackendTransportError("callback failed"),
        )
        runner = FakeJobRunner(
            result=WorkerProcessingPayload(
                execution_id=105,
                job_id=12,
                worker_id="processing-worker-1",
                processing_version=2,
                duration_sec=120,
                language="en",
                video_path="/tmp/video.mp4",
                audio_path="/tmp/audio.wav",
                transcript_segments=[],
                silence_segments=[],
                analysis_windows=[],
                clip_candidates=[],
            )
        )
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="processing-worker-1",
            worker_role="processing",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(backend.claim_calls, 1)
        self.assertEqual(len(backend.results), 0)
        self.assertEqual(len(backend.failures), 0)

    def test_polling_loop_treats_conflict_callback_as_lost_lease(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=107,
                job_id=13,
                processing_version=2,
                task_type="ANALYZE",
                source_type="FILE",
                video_path=Path("/tmp/video.mp4"),
                source_url=None,
            ),
            result_error=BackendTransportError("Backend request failed with HTTP 409", status_code=409),
        )
        runner = FakeJobRunner(
            result=WorkerProcessingPayload(
                execution_id=107,
                job_id=13,
                worker_id="processing-worker-1",
                processing_version=2,
                duration_sec=120,
                language="en",
                video_path="/tmp/video.mp4",
                audio_path="/tmp/audio.wav",
                transcript_segments=[],
                silence_segments=[],
                analysis_windows=[],
                clip_candidates=[],
            )
        )
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="processing-worker-1",
            worker_role="processing",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(backend.claim_calls, 1)
        self.assertEqual(len(backend.results), 0)
        self.assertEqual(len(backend.failures), 0)

    def test_polling_loop_survives_failure_callback_transport_error(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=108,
                job_id=14,
                processing_version=1,
                task_type="DOWNLOAD",
                source_type="URL",
                video_path=None,
                source_url="https://example.com/video.mp4",
            )
        )
        backend.result_error = BackendTransportError("not used")
        original_submit_failure = backend.submit_failure

        def failing_submit_failure(payload):
            raise BackendTransportError("Backend request failed with HTTP 409", status_code=409)

        backend.submit_failure = failing_submit_failure  # type: ignore[method-assign]
        runner = FakeJobRunner(error=WorkerJobRunnerError("DOWNLOADING", "download failed"))
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="download-worker-1",
            worker_role="download",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(backend.claim_calls, 1)
        self.assertEqual(len(backend.failures), 0)

    def test_polling_loop_submits_export_result(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                execution_id=106,
                job_id=9,
                processing_version=5,
                task_type="EXPORT",
                source_type="FILE",
                video_path=Path("/tmp/video.mp4"),
                source_url=None,
                candidate_id=3,
                clip_start_sec=5.0,
                clip_end_sec=12.0,
                artifact_path=Path("/tmp/candidate-3.mp4"),
            )
        )
        runner = FakeJobRunner(
            result=WorkerExportCompletionPayload(
                execution_id=106,
                job_id=9,
                worker_id="processing-worker-1",
                processing_version=5,
                candidate_id=3,
                artifact_path="/tmp/candidate-3.mp4",
            )
        )
        loop = WorkerPollingLoop(
            backend_client=backend,
            job_runner=runner,
            worker_id="processing-worker-1",
            worker_role="processing",
            poll_interval_sec=0,
        )

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(len(backend.export_results), 1)
