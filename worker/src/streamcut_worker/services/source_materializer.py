from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from urllib import parse, request

from streamcut_worker.models import ClaimedJob


class SourceMaterializationError(RuntimeError):
    def __init__(self, message: str, *, failed_state: str = "DOWNLOADING") -> None:
        super().__init__(message)
        self.failed_state = failed_state


@dataclass(slots=True)
class SourceMaterializer:
    storage_root: Path

    def materialize(self, job: ClaimedJob) -> Path:
        if job.source_type == "FILE":
            if job.video_path is None:
                raise SourceMaterializationError(
                    f"FILE job {job.job_id} is missing videoPath",
                )
            if not job.video_path.exists():
                raise SourceMaterializationError(
                    f"Input video does not exist: {job.video_path}",
                )
            return job.video_path

        if job.source_type == "URL":
            if not job.source_url:
                raise SourceMaterializationError(
                    f"URL job {job.job_id} is missing sourceUrl",
                )
            target_path = self._resolve_download_path(job)
            target_path.parent.mkdir(parents=True, exist_ok=True)
            with request.urlopen(job.source_url) as response, target_path.open("wb") as output:
                output.write(response.read())
            return target_path

        raise SourceMaterializationError(
            f"Unsupported source type for job {job.job_id}: {job.source_type}",
        )

    def _resolve_download_path(self, job: ClaimedJob) -> Path:
        parsed = parse.urlparse(job.source_url or "")
        suffix = Path(parsed.path).suffix or ".mp4"
        return self.storage_root / "jobs" / str(job.job_id) / "source" / f"source-video{suffix}"
