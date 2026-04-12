FROM public.ecr.aws/docker/library/python:3.12.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV VIRTUAL_ENV=/opt/venv
ENV PATH="/opt/venv/bin:${PATH}"
ENV HF_HOME=/app/model-cache

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pyproject.toml ./
COPY src ./src

RUN python -m venv "${VIRTUAL_ENV}" \
    && pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir yt-dlp \
    && pip install --no-cache-dir . \
    && yt-dlp --version

CMD ["python", "-m", "streamcut_worker"]