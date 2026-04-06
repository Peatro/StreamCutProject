import logging
import os
from pathlib import Path
import signal

from streamcut_worker.pipeline import WorkerPollingLoop, create_default_job_runner
from streamcut_worker.services import BackendClient

_running = True


def _request_shutdown(signum: int, _frame: object) -> None:
    global _running
    _running = False
    logging.info("Shutdown signal received: %s", signum)


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    logging.info("StreamCut worker started")
    signal.signal(signal.SIGTERM, _request_shutdown)
    signal.signal(signal.SIGINT, _request_shutdown)

    backend_base_url = os.getenv("BACKEND_BASE_URL", "http://backend:8080")
    storage_root = Path(os.getenv("APP_STORAGE_LOCAL_ROOT", "/data/storage"))
    worker_id = os.getenv("WORKER_ID", "worker-1")
    poll_interval_sec = float(os.getenv("WORKER_POLL_INTERVAL_SEC", "5"))
    emotion_keywords = tuple(
        keyword.strip()
        for keyword in os.getenv("WORKER_EMOTION_KEYWORDS", "").split(",")
        if keyword.strip()
    )

    polling_loop = WorkerPollingLoop(
        backend_client=BackendClient(base_url=backend_base_url),
        job_runner=create_default_job_runner(
            storage_root=storage_root,
            emotion_keywords=emotion_keywords,
        ),
        worker_id=worker_id,
        poll_interval_sec=poll_interval_sec,
    )

    polling_loop.run_forever(lambda: _running)

    logging.info("StreamCut worker stopped")


if __name__ == "__main__":
    main()
