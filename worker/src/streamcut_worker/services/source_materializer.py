from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Protocol
from urllib import parse, request

from streamcut_worker.models import ClaimedJob


class SourceMaterializationError(RuntimeError):
    def __init__(self, message: str, *, failed_state: str = "DOWNLOADING") -> None:
        super().__init__(message)
        self.failed_state = failed_state


class PlatformVideoDownloader(Protocol):
    def download(
        self,
        source_url: str,
        target_dir: Path,
        filename_stem: str,
        on_progress: "Callable[[float], None] | None" = None,
    ) -> Path: ...


class YtDlpPlatformDownloader:
    def download(
        self,
        source_url: str,
        target_dir: Path,
        filename_stem: str,
        on_progress: "Callable[[float], None] | None" = None,
    ) -> Path:
        try:
            from yt_dlp import YoutubeDL
        except ImportError as exc:
            raise SourceMaterializationError(
                "yt-dlp is required to download platform video URLs",
            ) from exc

        target_dir.mkdir(parents=True, exist_ok=True)
        output_template = str(target_dir / f"{filename_stem}.%(ext)s")

        def _yt_dlp_hook(info: dict) -> None:
            if on_progress is None or info.get("status") != "downloading":
                return
            downloaded = info.get("downloaded_bytes") or 0
            total = info.get("total_bytes") or info.get("total_bytes_estimate") or 0
            if total > 0:
                on_progress(min(downloaded / total * 100.0, 100.0))

        options = {
            "outtmpl": output_template,
            "quiet": True,
            "no_warnings": True,
            "format": "bestvideo[height<=1080]*+bestaudio/best[height<=1080]",
            "merge_output_format": "mp4",
            "concurrent_fragment_downloads": 4,
            "restrictfilenames": True,
            "noplaylist": True,
            "progress_hooks": [_yt_dlp_hook],
        }

        try:
            with YoutubeDL(options) as ydl:
                ydl.extract_info(source_url, download=True)
        except Exception as exc:
            raise SourceMaterializationError(
                f"platform download failed for {source_url}: {exc}",
            ) from exc

        candidates = sorted(
            (
                path
                for path in target_dir.iterdir()
                if path.is_file() and path.suffix.lower() not in {".part", ".ytdl", ".tmp"}
            ),
            key=lambda path: (path.stat().st_mtime, path.stat().st_size),
            reverse=True,
        )
        if not candidates:
            raise SourceMaterializationError(
                f"platform download did not produce a media file for {source_url}",
            )
        return candidates[0]


@dataclass(slots=True)
class SourceMaterializer:
    storage_root: Path
    platform_downloader: PlatformVideoDownloader | None = None

    def materialize(
        self,
        job: ClaimedJob,
        on_progress: Callable[[float], None] | None = None,
        *,
        allow_origin_download: bool = True,
    ) -> Path:
        if not allow_origin_download:
            return self._materialize_durable_source(job)

        if job.source_type == "FILE":
            if job.video_path is not None and job.video_path.exists():
                return job.video_path
            if job.video_download_url:
                return self._download_from_durable_reference(job)
            if job.video_path is None:
                raise SourceMaterializationError(
                    f"FILE job {job.job_id} is missing videoPath",
                )
            raise SourceMaterializationError(
                f"Input video does not exist: {job.video_path}",
            )

        if job.source_type == "URL":
            if not job.source_url:
                raise SourceMaterializationError(
                    f"URL job {job.job_id} is missing sourceUrl",
                )
            return self._materialize_url(job, on_progress)

        raise SourceMaterializationError(
            f"Unsupported source type for job {job.job_id}: {job.source_type}",
        )

    def _materialize_durable_source(self, job: ClaimedJob) -> Path:
        if job.video_path is not None and job.video_path.exists():
            return job.video_path
        if job.video_download_url:
            return self._download_from_durable_reference(job)
        raise SourceMaterializationError(
            f"Job {job.job_id} is missing a durable source download URL",
            failed_state="EXTRACTING_AUDIO",
        )

    def _materialize_url(
        self,
        job: ClaimedJob,
        on_progress: Callable[[float], None] | None = None,
    ) -> Path:
        assert job.source_url is not None
        target_path = self._resolve_download_path(job)
        target_path.parent.mkdir(parents=True, exist_ok=True)

        if self._requires_platform_downloader(job.source_url):
            return self._download_with_platform_extractor(job.source_url, target_path.parent, on_progress)

        try:
            with request.urlopen(job.source_url) as response, target_path.open("wb") as output:
                output.write(response.read())
        except Exception as exc:
            raise SourceMaterializationError(
                f"source download failed for {job.source_url}: {exc}",
            ) from exc

        if self._looks_like_html(target_path):
            target_path.unlink(missing_ok=True)
            return self._download_with_platform_extractor(job.source_url, target_path.parent, on_progress)

        return target_path

    def _download_from_durable_reference(self, job: ClaimedJob) -> Path:
        assert job.video_download_url is not None
        target_path = self._resolve_durable_download_path(job)
        target_path.parent.mkdir(parents=True, exist_ok=True)

        if target_path.exists():
            return target_path

        try:
            with request.urlopen(job.video_download_url) as response, target_path.open("wb") as output:
                output.write(response.read())
        except Exception as exc:
            raise SourceMaterializationError(
                f"source download failed for {job.video_download_url}: {exc}",
                failed_state="EXTRACTING_AUDIO",
            ) from exc

        return target_path

    def _resolve_download_path(self, job: ClaimedJob) -> Path:
        parsed = parse.urlparse(job.source_url or "")
        suffix = Path(parsed.path).suffix or ".mp4"
        return self.storage_root / "jobs" / str(job.job_id) / "source" / f"source-video{suffix}"

    def _resolve_durable_download_path(self, job: ClaimedJob) -> Path:
        suffix = self._resolve_durable_suffix(job)
        return self.storage_root / "jobs" / str(job.job_id) / "source" / f"source-video{suffix}"

    def _resolve_durable_suffix(self, job: ClaimedJob) -> str:
        for candidate in (job.video_reference, str(job.video_path) if job.video_path is not None else None):
            if not candidate:
                continue
            suffix = Path(parse.urlparse(candidate).path).suffix
            if suffix:
                return suffix
        return ".mp4"

    def _requires_platform_downloader(self, source_url: str) -> bool:
        hostname = (parse.urlparse(source_url).hostname or "").lower()
        return any(
            host in hostname
            for host in (
                "twitch.tv",
                "youtube.com",
                "youtu.be",
                "vimeo.com",
            )
        )

    def _download_with_platform_extractor(
        self,
        source_url: str,
        target_dir: Path,
        on_progress: Callable[[float], None] | None = None,
    ) -> Path:
        downloader = self.platform_downloader or YtDlpPlatformDownloader()
        return downloader.download(source_url, target_dir, "source-video", on_progress)

    def _looks_like_html(self, path: Path) -> bool:
        prefix = path.read_bytes()[:512].lstrip().lower()
        return prefix.startswith(b"<!doctype html") or prefix.startswith(b"<html")
