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
    worker_role = os.getenv("WORKER_ROLE", "processing").strip().lower()
    poll_interval_sec = float(os.getenv("WORKER_POLL_INTERVAL_SEC", "5"))
    emotion_keywords = tuple(
        keyword.strip()
        for keyword in os.getenv("WORKER_EMOTION_KEYWORDS", "").split(",")
        if keyword.strip()
    )
    whisper_device = os.getenv("WHISPER_DEVICE", "cpu").strip().lower() or "cpu"
    whisper_compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8").strip() or "int8"

    if worker_role not in {"download", "processing"}:
        raise ValueError("WORKER_ROLE must be 'download' or 'processing'")

    if worker_role == "processing":
        logging.info(
            "Processing worker is preparing the transcription model cache at %s",
            os.getenv("HF_HOME", "/app/model-cache"),
        )

    job_runner = create_default_job_runner(
        storage_root=storage_root,
        emotion_keywords=emotion_keywords,
        load_transcription_model=(worker_role == "processing"),
        whisper_device=whisper_device,
        whisper_compute_type=whisper_compute_type,
    )

    if worker_role == "processing":
        logging.info("Processing worker transcription model is ready")

    polling_loop = WorkerPollingLoop(
        backend_client=BackendClient(base_url=backend_base_url),
        job_runner=job_runner,
        worker_id=worker_id,
        worker_role=worker_role,
        poll_interval_sec=poll_interval_sec,
        whisper_device=whisper_device if worker_role == "processing" else None,
    )

    polling_loop.run_forever(lambda: _running)

    logging.info("StreamCut worker stopped")


if __name__ == "__main__":
    main()
