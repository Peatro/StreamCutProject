import logging


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    logging.info("StreamCut worker started")


if __name__ == "__main__":
    main()
