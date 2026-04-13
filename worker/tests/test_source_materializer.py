from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
from urllib import error
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.models import ClaimedJob
from streamcut_worker.services import source_materializer as source_materializer_module
from streamcut_worker.services.source_materializer import SourceMaterializationError, SourceMaterializer


class FakePlatformDownloader:
    def __init__(self, downloaded_name: str = "source-video.mp4") -> None:
        self.downloaded_name = downloaded_name
        self.calls: list[str] = []

    def download(self, source_url: str, target_dir: Path, filename_stem: str, on_progress=None) -> Path:
        self.calls.append(source_url)
        target_dir.mkdir(parents=True, exist_ok=True)
        output_path = target_dir / self.downloaded_name
        output_path.write_bytes(b"video-data")
        return output_path


class FakeResponse:
    def __init__(self, payload: bytes) -> None:
        self.payload = payload

    def read(self) -> bytes:
        return self.payload

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        return None


class SourceMaterializerTests(unittest.TestCase):
    def test_url_direct_media_download_is_stored_without_platform_extractor(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            downloader = FakePlatformDownloader()

            @contextmanager
            def fake_urlopen(url: str):
                yield FakeResponse(b"\x00\x00\x00\x18ftypmp42video")

            materializer = SourceMaterializer(
                storage_root=storage_root,
                platform_downloader=downloader,
            )
            original_urlopen = source_materializer_module.request.urlopen
            source_materializer_module.request.urlopen = fake_urlopen
            try:
                result = materializer.materialize(
                    ClaimedJob(
                        execution_id=301,
                        job_id=11,
                        processing_version=1,
                        task_type="ANALYZE",
                        source_type="URL",
                        video_path=None,
                        source_url="https://example.com/video.mp4",
                    )
                )
                self.assertTrue(result.exists())
                self.assertEqual(result.name, "source-video.mp4")
                self.assertEqual(downloader.calls, [])
            finally:
                source_materializer_module.request.urlopen = original_urlopen

    def test_twitch_url_uses_platform_extractor(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            downloader = FakePlatformDownloader()
            materializer = SourceMaterializer(
                storage_root=storage_root,
                platform_downloader=downloader,
            )

            result = materializer.materialize(
                ClaimedJob(
                    execution_id=302,
                    job_id=12,
                    processing_version=1,
                    task_type="ANALYZE",
                    source_type="URL",
                    video_path=None,
                    source_url="https://www.twitch.tv/videos/2735588522",
                )
            )

            self.assertEqual(downloader.calls, ["https://www.twitch.tv/videos/2735588522"])
            self.assertTrue(result.exists())
            self.assertEqual(result.name, "source-video.mp4")

    def test_html_download_falls_back_to_platform_extractor(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            downloader = FakePlatformDownloader()

            @contextmanager
            def fake_urlopen(url: str):
                yield FakeResponse(b"<!DOCTYPE html><html><body>not a video</body></html>")

            materializer = SourceMaterializer(
                storage_root=storage_root,
                platform_downloader=downloader,
            )
            original_urlopen = source_materializer_module.request.urlopen
            source_materializer_module.request.urlopen = fake_urlopen
            try:
                result = materializer.materialize(
                    ClaimedJob(
                        execution_id=303,
                        job_id=13,
                        processing_version=1,
                        task_type="ANALYZE",
                        source_type="URL",
                        video_path=None,
                        source_url="https://example.com/watch?v=123",
                    )
                )
                self.assertEqual(downloader.calls, ["https://example.com/watch?v=123"])
                self.assertTrue(result.exists())
                self.assertEqual(result.read_bytes(), b"video-data")
            finally:
                source_materializer_module.request.urlopen = original_urlopen

    def test_url_download_errors_are_wrapped_with_stage_context(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            materializer = SourceMaterializer(storage_root=storage_root)

            def fake_urlopen(url: str):
                raise error.URLError("connection refused")

            original_urlopen = source_materializer_module.request.urlopen
            source_materializer_module.request.urlopen = fake_urlopen
            try:
                with self.assertRaises(SourceMaterializationError) as ctx:
                    materializer.materialize(
                        ClaimedJob(
                            execution_id=304,
                            job_id=14,
                            processing_version=1,
                            task_type="ANALYZE",
                            source_type="URL",
                            video_path=None,
                            source_url="http://localhost:65534/nope",
                        )
                    )
            finally:
                source_materializer_module.request.urlopen = original_urlopen

        self.assertEqual(ctx.exception.failed_state, "DOWNLOADING")
        self.assertIn("source download failed", str(ctx.exception))
        self.assertIn("connection refused", str(ctx.exception))

    def test_file_job_falls_back_to_durable_source_download_when_local_path_is_missing(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)

            @contextmanager
            def fake_urlopen(url: str):
                yield FakeResponse(b"video-data")

            materializer = SourceMaterializer(storage_root=storage_root)
            original_urlopen = source_materializer_module.request.urlopen
            source_materializer_module.request.urlopen = fake_urlopen
            try:
                result = materializer.materialize(
                    ClaimedJob(
                        execution_id=305,
                        job_id=15,
                        processing_version=1,
                        task_type="DOWNLOAD",
                        source_type="FILE",
                        video_path=storage_root / "missing.mp4",
                        source_url=None,
                        video_reference="s3://streamcut-artifacts/sources/jobs/15/source-video.mp4",
                        video_download_url="http://backend:8080/api/internal/worker/jobs/15/source/file",
                    )
                )
                self.assertTrue(result.exists())
                self.assertEqual(result.read_bytes(), b"video-data")
            finally:
                source_materializer_module.request.urlopen = original_urlopen

    def test_durable_source_download_is_used_for_analyze_jobs_when_local_path_is_missing(self) -> None:
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)

            @contextmanager
            def fake_urlopen(url: str):
                yield FakeResponse(b"video-data")

            materializer = SourceMaterializer(storage_root=storage_root)
            original_urlopen = source_materializer_module.request.urlopen
            source_materializer_module.request.urlopen = fake_urlopen
            try:
                result = materializer.materialize(
                    ClaimedJob(
                        execution_id=306,
                        job_id=16,
                        processing_version=1,
                        task_type="ANALYZE",
                        source_type="URL",
                        video_path=storage_root / "missing.mp4",
                        source_url="https://example.com/origin.mp4",
                        video_reference="s3://streamcut-artifacts/sources/jobs/16/source-video.mp4",
                        video_download_url="http://backend:8080/api/internal/worker/jobs/16/source/file",
                    ),
                    allow_origin_download=False,
                )
                self.assertTrue(result.exists())
                self.assertEqual(result.read_bytes(), b"video-data")
            finally:
                source_materializer_module.request.urlopen = original_urlopen


if __name__ == "__main__":
    unittest.main()
