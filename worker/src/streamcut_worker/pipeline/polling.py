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
    whisper_device: str | None = None

    @staticmethod
    def _is_lost_lease(exc: BackendTransportError) -> bool:
        return exc.is_conflict

    def _log_lost_lease(self, job_id: int, exc: BackendTransportError) -> None:
        logging.warning(
            "job_lease_lost jobId=%s workerId=%s workerRole=%s message=%s",
            job_id,
            self.worker_id,
            self.worker_role,
            exc,
        )

    def run_forever(self, should_continue: Callable[[], bool]) -> None:
        while should_continue():
            try:
                claimed_job = self.backend_client.claim_next_job(self.worker_id, self.worker_role, self.whisper_device)
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
                            execution_id=claimed_job.execution_id,
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
                try:
                    self.backend_client.submit_failure(
                        WorkerFailurePayload(
                            execution_id=claimed_job.execution_id,
                            job_id=claimed_job.job_id,
                            worker_id=self.worker_id,
                            processing_version=claimed_job.processing_version,
                            failed_state=exc.failed_state,
                            message=str(exc),
                        )
                    )
                except BackendTransportError as callback_exc:
                    if self._is_lost_lease(callback_exc):
                        self._log_lost_lease(claimed_job.job_id, callback_exc)
                    else:
                        logging.warning(
                            "Worker failure callback failed for job %s: %s",
                            claimed_job.job_id,
                            callback_exc,
                        )
            except BackendTransportError as exc:
                if self._is_lost_lease(exc):
                    self._log_lost_lease(claimed_job.job_id, exc)
                else:
                    logging.warning("Worker callback failed for job %s: %s", claimed_job.job_id, exc)
            except Exception as exc:
                logging.exception("Worker crashed unexpectedly while running job %s", claimed_job.job_id)
                try:
                    self.backend_client.submit_failure(
                        WorkerFailurePayload(
                            execution_id=claimed_job.execution_id,
                            job_id=claimed_job.job_id,
                            worker_id=self.worker_id,
                            processing_version=claimed_job.processing_version,
                            failed_state="WORKER_INTERNAL",
                            message=f"Unexpected worker failure: {exc}",
                        )
                    )
                except BackendTransportError as callback_exc:
                    if self._is_lost_lease(callback_exc):
                        self._log_lost_lease(claimed_job.job_id, callback_exc)
                    else:
                        logging.warning(
                            "Worker internal failure callback failed for job %s: %s",
                            claimed_job.job_id,
                            callback_exc,
                        )
