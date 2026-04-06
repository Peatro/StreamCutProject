from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.models import ClaimedJob, WorkerExportCompletionPayload, WorkerFailurePayload, WorkerProcessingPayload
from streamcut_worker.pipeline import WorkerPollingLoop, WorkerJobRunnerError


class FakeBackendClient:
    def __init__(self, claimed_job=None) -> None:
        self.claimed_job = claimed_job
        self.claim_calls = 0
        self.results = []
        self.export_results = []
        self.failures = []

    def claim_next_job(self, worker_id: str):
        self.claim_calls += 1
        claimed_job = self.claimed_job
        self.claimed_job = None
        return claimed_job

    def submit_result(self, payload: WorkerProcessingPayload):
        self.results.append(payload)
        return {"status": "READY_FOR_REVIEW"}

    def submit_failure(self, payload: WorkerFailurePayload):
        self.failures.append(payload)
        return {"status": "FAILED"}

    def submit_export_result(self, payload):
        self.export_results.append(payload)
        return {"status": "COMPLETED"}


class FakeJobRunner:
    def __init__(self, result=None, error: Exception | None = None) -> None:
        self.result = result
        self.error = error

    def run(self, job: ClaimedJob):
        if self.error is not None:
            raise self.error
        return self.result


class WorkerPollingLoopTests(unittest.TestCase):
    def test_polling_loop_claims_job_and_submits_result(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(7, "ANALYZE", "FILE", Path("/tmp/video.mp4"), None)
        )
        runner = FakeJobRunner(
            result=WorkerProcessingPayload(
                job_id=7,
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
        loop = WorkerPollingLoop(backend_client=backend, job_runner=runner, worker_id="worker-1", poll_interval_sec=0)

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(backend.claim_calls, 1)
        self.assertEqual(len(backend.results), 1)
        self.assertEqual(backend.results[0].job_id, 7)

    def test_polling_loop_reports_failures(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(8, "ANALYZE", "URL", None, "https://example.com/video.mp4")
        )
        runner = FakeJobRunner(
            error=WorkerJobRunnerError("DOWNLOADING", "download failed")
        )
        loop = WorkerPollingLoop(backend_client=backend, job_runner=runner, worker_id="worker-1", poll_interval_sec=0)

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(len(backend.failures), 1)
        self.assertEqual(backend.failures[0].job_id, 8)
        self.assertEqual(backend.failures[0].failed_state, "DOWNLOADING")

    def test_polling_loop_submits_export_result(self) -> None:
        backend = FakeBackendClient(
            claimed_job=ClaimedJob(
                9,
                "EXPORT",
                "FILE",
                Path("/tmp/video.mp4"),
                None,
                candidate_id=3,
                clip_start_sec=5.0,
                clip_end_sec=12.0,
                artifact_path=Path("/tmp/candidate-3.mp4"),
            )
        )

        runner = FakeJobRunner(result=WorkerExportCompletionPayload(9, 3, "/tmp/candidate-3.mp4"))
        loop = WorkerPollingLoop(backend_client=backend, job_runner=runner, worker_id="worker-1", poll_interval_sec=0)

        iterations = iter([True, False])
        loop.run_forever(lambda: next(iterations))

        self.assertEqual(len(backend.export_results), 1)
