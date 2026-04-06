from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from typing import Callable

from streamcut_worker.models import WorkerExportCompletionPayload, WorkerFailurePayload
from streamcut_worker.pipeline.job_runner import WorkerJobRunner, WorkerJobRunnerError
from streamcut_worker.services.backend_client import BackendClient, BackendTransportError


@dataclass(slots=True)
class WorkerPollingLoop:
    backend_client: BackendClient
    job_runner: WorkerJobRunner
    worker_id: str
    poll_interval_sec: float = 5.0

    def run_forever(self, should_continue: Callable[[], bool]) -> None:
        while should_continue():
            try:
                claimed_job = self.backend_client.claim_next_job(self.worker_id)
            except BackendTransportError as exc:
                logging.warning("Worker claim failed: %s", exc)
                time.sleep(self.poll_interval_sec)
                continue

            if claimed_job is None:
                time.sleep(self.poll_interval_sec)
                continue

            try:
                result = self.job_runner.run(claimed_job)
                if isinstance(result, WorkerExportCompletionPayload):
                    self.backend_client.submit_export_result(result)
                else:
                    self.backend_client.submit_result(result)
            except WorkerJobRunnerError as exc:
                self.backend_client.submit_failure(
                    WorkerFailurePayload(
                        job_id=claimed_job.job_id,
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
                        failed_state="WORKER_INTERNAL",
                        message=f"Unexpected worker failure: {exc}",
                    )
                )
