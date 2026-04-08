from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from typing import Callable

from streamcut_worker.models import (
    WorkerDownloadCompletionPayload,
    WorkerExportCompletionPayload,
    WorkerFailurePayload,
    WorkerProgressPayload,
)
from streamcut_worker.pipeline.job_runner import WorkerJobRunner, WorkerJobRunnerError
from streamcut_worker.services.backend_client import BackendClient, BackendTransportError


@dataclass(slots=True)
class WorkerPollingLoop:
    backend_client: BackendClient
    job_runner: WorkerJobRunner
    worker_id: str
    worker_role: str
    poll_interval_sec: float = 5.0

    def run_forever(self, should_continue: Callable[[], bool]) -> None:
        while should_continue():
            try:
                claimed_job = self.backend_client.claim_next_job(self.worker_id, self.worker_role)
            except BackendTransportError as exc:
                logging.warning("Worker claim failed: %s", exc)
                time.sleep(self.poll_interval_sec)
                continue

            if claimed_job is None:
                time.sleep(self.poll_interval_sec)
                continue

            logging.info(
                "job_claimed jobId=%s taskType=%s sourceType=%s workerId=%s",
                claimed_job.job_id,
                claimed_job.task_type,
                claimed_job.source_type,
                self.worker_id,
            )

            try:
                result = self.job_runner.run(
                    claimed_job,
                    lambda status, progress_percent, message: self.backend_client.submit_progress(
                        WorkerProgressPayload(
                            job_id=claimed_job.job_id,
                            worker_id=self.worker_id,
                            processing_version=claimed_job.processing_version,
                            status=status,
                            progress_percent=progress_percent,
                            message=message,
                        )
                    ),
                    worker_id=self.worker_id,
                )
                if isinstance(result, WorkerDownloadCompletionPayload):
                    logging.info(
                        "download_result_ready jobId=%s videoPath=%s",
                        result.job_id,
                        result.video_path,
                    )
                    self.backend_client.submit_download_result(result)
                elif isinstance(result, WorkerExportCompletionPayload):
                    logging.info(
                        "export_result_ready jobId=%s candidateId=%s artifactPath=%s",
                        result.job_id,
                        result.candidate_id,
                        result.artifact_path,
                    )
                    self.backend_client.submit_export_result(result)
                else:
                    logging.info(
                        "job_result_ready jobId=%s transcriptSegments=%s silenceSegments=%s analysisWindows=%s clipCandidates=%s",
                        result.job_id,
                        len(result.transcript_segments),
                        len(result.silence_segments),
                        len(result.analysis_windows),
                        len(result.clip_candidates),
                    )
                    self.backend_client.submit_result(result)
            except WorkerJobRunnerError as exc:
                logging.warning(
                    "job_failed jobId=%s failedState=%s message=%s",
                    claimed_job.job_id,
                    exc.failed_state,
                    exc,
                )
                self.backend_client.submit_failure(
                    WorkerFailurePayload(
                        job_id=claimed_job.job_id,
                        worker_id=self.worker_id,
                        processing_version=claimed_job.processing_version,
                        failed_state=exc.failed_state,
                        message=str(exc),
                    )
                )
            except BackendTransportError as exc:
                logging.warning("Worker callback failed for job %s: %s", claimed_job.job_id, exc)
            except Exception as exc:
                logging.exception("Worker crashed unexpectedly while running job %s", claimed_job.job_id)
                self.backend_client.submit_failure(
                    WorkerFailurePayload(
                        job_id=claimed_job.job_id,
                        worker_id=self.worker_id,
                        processing_version=claimed_job.processing_version,
                        failed_state="WORKER_INTERNAL",
                        message=f"Unexpected worker failure: {exc}",
                    )
                )
