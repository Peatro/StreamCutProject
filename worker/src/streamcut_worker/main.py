import logging
import signal
import time


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

    while _running:
        time.sleep(1)

    logging.info("StreamCut worker stopped")


if __name__ == "__main__":
    main()
