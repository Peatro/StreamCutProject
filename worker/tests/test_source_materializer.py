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
from streamcut_worker.services.source_materializer import (
    SourceMaterializationError,
    SourceMaterializer,
    TwitchDownloaderCliDownloader,
    YtDlpPlatformDownloader,
)


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

    def test_yt_dlp_format_prefers_muxed_single_stream(self) -> None:
        """YtDlpPlatformDownloader must request a pre-muxed format first, merge as fallback."""
        captured_options: list[dict] = []

        class FakeYoutubeDL:
            def __init__(self, options: dict) -> None:
                captured_options.append(dict(options))

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return None

            def extract_info(self, url: str, download: bool = True) -> None:
                pass

        import types

        fake_module = types.ModuleType("yt_dlp")
        fake_module.YoutubeDL = FakeYoutubeDL
        sys.modules["yt_dlp"] = fake_module

        try:
            with TemporaryDirectory() as temp_dir:
                target_dir = Path(temp_dir)
                (target_dir / "source-video.mp4").write_bytes(b"fake")
                downloader = YtDlpPlatformDownloader()
                downloader.download("https://www.twitch.tv/videos/123", target_dir, "source-video")

            self.assertEqual(len(captured_options), 1)
            self.assertEqual(
                captured_options[0]["format"],
                "best[height<=1080]/bestvideo[height<=1080]*+bestaudio",
                "Format must prefer pre-muxed (best) first, merge as fallback",
            )
        finally:
            del sys.modules["yt_dlp"]


class YtDlpProgressGatingTests(unittest.TestCase):
    """TASK-077: download heartbeat must be gated on real byte progress."""

    def _make_downloader_and_hook(self):
        """Create a YtDlpPlatformDownloader and extract the _yt_dlp_hook closure.

        Returns (progress_calls, hook) where progress_calls is a list that
        collects every on_progress(percent) invocation.
        """
        progress_calls: list[float] = []

        captured_hooks: list = []
        original_init = None

        class CapturingYoutubeDL:
            def __init__(self, options: dict) -> None:
                captured_hooks.extend(options.get("progress_hooks", []))

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return None

            def extract_info(self, url: str, download: bool = True) -> None:
                pass

        import types
        fake_module = types.ModuleType("yt_dlp")
        fake_module.YoutubeDL = CapturingYoutubeDL
        sys.modules["yt_dlp"] = fake_module

        try:
            downloader = YtDlpPlatformDownloader()
            with TemporaryDirectory() as temp_dir:
                target_dir = Path(temp_dir)
                (target_dir / "source-video.mp4").write_bytes(b"fake")
                downloader.download(
                    "https://www.twitch.tv/videos/123",
                    target_dir,
                    "source-video",
                    on_progress=lambda pct: progress_calls.append(pct),
                )
        finally:
            del sys.modules["yt_dlp"]

        self.assertEqual(len(captured_hooks), 1, "Expected exactly one progress hook")
        return progress_calls, captured_hooks[0]

    def test_stalled_download_does_not_heartbeat(self) -> None:
        """Repeated downloading events with non-increasing downloaded_bytes
        must NOT invoke on_progress after the first real progress event."""
        progress_calls, hook = self._make_downloader_and_hook()

        # First real progress event -- should invoke on_progress
        hook({"status": "downloading", "downloaded_bytes": 1000, "total_bytes": 10000})
        self.assertEqual(len(progress_calls), 1)
        self.assertAlmostEqual(progress_calls[0], 10.0)

        # Stalled: same byte count repeated many times
        for _ in range(20):
            hook({"status": "downloading", "downloaded_bytes": 1000, "total_bytes": 10000})

        # on_progress must NOT have been called again
        self.assertEqual(
            len(progress_calls), 1,
            f"Expected 1 progress call during stall, got {len(progress_calls)}",
        )

    def test_advancing_download_heartbeats_with_correct_percentage(self) -> None:
        """Increasing downloaded_bytes must invoke on_progress with correct %."""
        progress_calls, hook = self._make_downloader_and_hook()

        hook({"status": "downloading", "downloaded_bytes": 2000, "total_bytes": 10000})
        hook({"status": "downloading", "downloaded_bytes": 5000, "total_bytes": 10000})
        hook({"status": "downloading", "downloaded_bytes": 10000, "total_bytes": 10000})

        self.assertEqual(len(progress_calls), 3)
        self.assertAlmostEqual(progress_calls[0], 20.0)
        self.assertAlmostEqual(progress_calls[1], 50.0)
        self.assertAlmostEqual(progress_calls[2], 100.0)

    def test_stall_then_resume_heartbeats_correctly(self) -> None:
        """After a stall period, resumed progress must heartbeat again."""
        progress_calls, hook = self._make_downloader_and_hook()

        # Initial progress
        hook({"status": "downloading", "downloaded_bytes": 1000, "total_bytes": 10000})
        self.assertEqual(len(progress_calls), 1)

        # Stall (5 ticks at same byte count)
        for _ in range(5):
            hook({"status": "downloading", "downloaded_bytes": 1000, "total_bytes": 10000})
        self.assertEqual(len(progress_calls), 1, "Stall should not produce extra heartbeats")

        # Resume
        hook({"status": "downloading", "downloaded_bytes": 3000, "total_bytes": 10000})
        self.assertEqual(len(progress_calls), 2)
        self.assertAlmostEqual(progress_calls[1], 30.0)

    def test_zero_downloaded_bytes_does_not_heartbeat(self) -> None:
        """Events with downloaded_bytes=0 must not invoke on_progress."""
        progress_calls, hook = self._make_downloader_and_hook()

        hook({"status": "downloading", "downloaded_bytes": 0, "total_bytes": 10000})
        hook({"status": "downloading", "downloaded_bytes": 0, "total_bytes": 10000})

        self.assertEqual(len(progress_calls), 0, "Zero bytes should not heartbeat")

    def test_byte_fluctuation_below_max_does_not_heartbeat(self) -> None:
        """Per-fragment byte fluctuations below the max must not heartbeat."""
        progress_calls, hook = self._make_downloader_and_hook()

        hook({"status": "downloading", "downloaded_bytes": 5000, "total_bytes": 10000})
        self.assertEqual(len(progress_calls), 1)

        # Fluctuation: bytes drop below max (fragment retry)
        hook({"status": "downloading", "downloaded_bytes": 3000, "total_bytes": 10000})
        hook({"status": "downloading", "downloaded_bytes": 4000, "total_bytes": 10000})
        self.assertEqual(len(progress_calls), 1, "Fluctuation below max should not heartbeat")

        # Genuine new progress above max
        hook({"status": "downloading", "downloaded_bytes": 6000, "total_bytes": 10000})
        self.assertEqual(len(progress_calls), 2)
        self.assertAlmostEqual(progress_calls[1], 60.0)

    def test_non_downloading_status_ignored(self) -> None:
        """Events with status != 'downloading' must be ignored regardless."""
        progress_calls, hook = self._make_downloader_and_hook()

        hook({"status": "finished", "downloaded_bytes": 10000, "total_bytes": 10000})
        hook({"status": "error", "downloaded_bytes": 500, "total_bytes": 10000})

        self.assertEqual(len(progress_calls), 0)

    def test_missing_total_bytes_does_not_heartbeat(self) -> None:
        """When total_bytes is unknown (0), on_progress should not be called
        even if downloaded_bytes increases -- percentage cannot be computed."""
        progress_calls, hook = self._make_downloader_and_hook()

        hook({"status": "downloading", "downloaded_bytes": 1000, "total_bytes": 0})
        hook({"status": "downloading", "downloaded_bytes": 2000})

        self.assertEqual(len(progress_calls), 0,
                         "No heartbeat expected when total_bytes is unknown")


class TwitchDownloaderRoutingTests(unittest.TestCase):
    """TASK-081: Twitch VODs route to TwitchDownloaderCLI; others to yt-dlp."""

    def test_twitch_vod_url_selects_twitch_downloader(self) -> None:
        downloader = SourceMaterializer._select_downloader(
            "https://www.twitch.tv/videos/2735588522",
        )
        self.assertIsInstance(downloader, TwitchDownloaderCliDownloader)

    def test_youtube_url_selects_ytdlp(self) -> None:
        downloader = SourceMaterializer._select_downloader(
            "https://www.youtube.com/watch?v=abc123",
        )
        self.assertIsInstance(downloader, YtDlpPlatformDownloader)

    def test_vimeo_url_selects_ytdlp(self) -> None:
        downloader = SourceMaterializer._select_downloader(
            "https://vimeo.com/123456789",
        )
        self.assertIsInstance(downloader, YtDlpPlatformDownloader)

    def test_generic_url_selects_ytdlp(self) -> None:
        downloader = SourceMaterializer._select_downloader(
            "https://example.com/video.mp4",
        )
        self.assertIsInstance(downloader, YtDlpPlatformDownloader)

    def test_twitch_clip_url_does_not_select_twitch_downloader(self) -> None:
        """Twitch clip URLs (not /videos/) should fall back to yt-dlp."""
        downloader = SourceMaterializer._select_downloader(
            "https://www.twitch.tv/streamer/clip/SomeClipSlug",
        )
        self.assertIsInstance(downloader, YtDlpPlatformDownloader)

    def test_twitch_vod_routes_to_twitch_downloader_in_materializer(self) -> None:
        """End-to-end: a Twitch VOD URL with no injected platform_downloader
        must route to TwitchDownloaderCliDownloader (not yt-dlp)."""
        with TemporaryDirectory() as temp_dir:
            storage_root = Path(temp_dir)
            materializer = SourceMaterializer(storage_root=storage_root)

            captured_cmd: list[list[str]] = []

            class FakePopen:
                def __init__(self, cmd, **kwargs):
                    captured_cmd.append(list(cmd))
                    self.stdout = iter([])
                    self.returncode = 0

                def wait(self):
                    return 0

                def kill(self):
                    pass

            import streamcut_worker.services.source_materializer as mod
            original_popen = mod.subprocess.Popen
            mod.subprocess.Popen = FakePopen
            try:
                target_dir = storage_root / "jobs" / "99" / "source"
                target_dir.mkdir(parents=True, exist_ok=True)
                (target_dir / "source-video.mp4").write_bytes(b"fake-video")

                result = materializer.materialize(
                    ClaimedJob(
                        execution_id=900,
                        job_id=99,
                        processing_version=1,
                        task_type="DOWNLOAD",
                        source_type="URL",
                        video_path=None,
                        source_url="https://www.twitch.tv/videos/2735588522",
                    )
                )
                self.assertTrue(result.exists())
                self.assertEqual(len(captured_cmd), 1)
                self.assertEqual(captured_cmd[0][0], "TwitchDownloaderCLI")
                self.assertIn("2735588522", captured_cmd[0])
            finally:
                mod.subprocess.Popen = original_popen


class TwitchVodIdParsingTests(unittest.TestCase):
    """TASK-081: Twitch VOD id extraction."""

    def test_standard_vod_url(self) -> None:
        vod_id = TwitchDownloaderCliDownloader.extract_vod_id(
            "https://www.twitch.tv/videos/2735588522",
        )
        self.assertEqual(vod_id, "2735588522")

    def test_vod_url_without_www(self) -> None:
        vod_id = TwitchDownloaderCliDownloader.extract_vod_id(
            "https://twitch.tv/videos/12345",
        )
        self.assertEqual(vod_id, "12345")

    def test_vod_url_with_query_params(self) -> None:
        vod_id = TwitchDownloaderCliDownloader.extract_vod_id(
            "https://www.twitch.tv/videos/99999?t=1h30m",
        )
        self.assertEqual(vod_id, "99999")

    def test_invalid_url_raises_error(self) -> None:
        with self.assertRaises(SourceMaterializationError) as ctx:
            TwitchDownloaderCliDownloader.extract_vod_id(
                "https://www.twitch.tv/streamer",
            )
        self.assertEqual(ctx.exception.failed_state, "DOWNLOADING")

    def test_clip_url_raises_error(self) -> None:
        with self.assertRaises(SourceMaterializationError):
            TwitchDownloaderCliDownloader.extract_vod_id(
                "https://www.twitch.tv/streamer/clip/SomeSlug",
            )


class TwitchDownloaderCommandBuildTests(unittest.TestCase):
    """TASK-081: TwitchDownloaderCLI command construction."""

    def test_command_args_are_correct(self) -> None:
        captured_cmd: list[list[str]] = []

        class FakePopen:
            def __init__(self, cmd, **kwargs):
                captured_cmd.append(list(cmd))
                self.stdout = iter([])
                self.returncode = 0

            def wait(self):
                return 0

            def kill(self):
                pass

        import streamcut_worker.services.source_materializer as mod
        original_popen = mod.subprocess.Popen
        mod.subprocess.Popen = FakePopen
        try:
            with TemporaryDirectory() as temp_dir:
                target_dir = Path(temp_dir)
                (target_dir / "source-video.mp4").write_bytes(b"fake")
                downloader = TwitchDownloaderCliDownloader()
                downloader.download(
                    "https://www.twitch.tv/videos/2735588522",
                    target_dir,
                    "source-video",
                )
        finally:
            mod.subprocess.Popen = original_popen

        self.assertEqual(len(captured_cmd), 1)
        cmd = captured_cmd[0]
        self.assertEqual(cmd[0], "TwitchDownloaderCLI")
        self.assertEqual(cmd[1], "videodownload")
        self.assertIn("--id", cmd)
        self.assertEqual(cmd[cmd.index("--id") + 1], "2735588522")
        self.assertIn("-q", cmd)
        self.assertEqual(cmd[cmd.index("-q") + 1], "1080p60")
        self.assertIn("-o", cmd)
        self.assertIn("--temp-path", cmd)
        self.assertIn("--ffmpeg-path", cmd)
        self.assertEqual(cmd[cmd.index("--ffmpeg-path") + 1], "ffmpeg")

    def test_failure_raises_source_materialization_error(self) -> None:
        class FakePopen:
            def __init__(self, cmd, **kwargs):
                self.stdout = iter([])
                self.returncode = 1

            def wait(self):
                return 1

            def kill(self):
                pass

        import streamcut_worker.services.source_materializer as mod
        original_popen = mod.subprocess.Popen
        mod.subprocess.Popen = FakePopen
        try:
            with TemporaryDirectory() as temp_dir:
                target_dir = Path(temp_dir)
                downloader = TwitchDownloaderCliDownloader()
                with self.assertRaises(SourceMaterializationError) as ctx:
                    downloader.download(
                        "https://www.twitch.tv/videos/123",
                        target_dir,
                        "source-video",
                    )
                self.assertEqual(ctx.exception.failed_state, "DOWNLOADING")
        finally:
            mod.subprocess.Popen = original_popen

    def test_launch_failure_raises_source_materialization_error(self) -> None:
        import streamcut_worker.services.source_materializer as mod
        original_popen = mod.subprocess.Popen

        def exploding_popen(*args, **kwargs):
            raise FileNotFoundError("TwitchDownloaderCLI not found")

        mod.subprocess.Popen = exploding_popen
        try:
            with TemporaryDirectory() as temp_dir:
                target_dir = Path(temp_dir)
                downloader = TwitchDownloaderCliDownloader()
                with self.assertRaises(SourceMaterializationError) as ctx:
                    downloader.download(
                        "https://www.twitch.tv/videos/123",
                        target_dir,
                        "source-video",
                    )
                self.assertEqual(ctx.exception.failed_state, "DOWNLOADING")
                self.assertIn("failed to launch", str(ctx.exception))
        finally:
            mod.subprocess.Popen = original_popen


class TwitchDownloaderProgressTests(unittest.TestCase):
    """TASK-081: progress parsing gates on monotonic increase."""

    def test_progress_reports_only_on_increase(self) -> None:
        progress_calls: list[float] = []

        class FakePopen:
            def __init__(self, cmd, **kwargs):
                self.stdout = iter([
                    "[STATUS] - Downloading 10.0%\n",
                    "[STATUS] - Downloading 10.0%\n",
                    "[STATUS] - Downloading 25.5%\n",
                    "[STATUS] - Downloading 20.0%\n",
                    "[STATUS] - Downloading 50.0%\n",
                    "[STATUS] - Downloading 100.0%\n",
                ])
                self.returncode = 0

            def wait(self):
                return 0

            def kill(self):
                pass

        import streamcut_worker.services.source_materializer as mod
        original_popen = mod.subprocess.Popen
        mod.subprocess.Popen = FakePopen
        try:
            with TemporaryDirectory() as temp_dir:
                target_dir = Path(temp_dir)
                (target_dir / "source-video.mp4").write_bytes(b"fake")
                downloader = TwitchDownloaderCliDownloader()
                downloader.download(
                    "https://www.twitch.tv/videos/123",
                    target_dir,
                    "source-video",
                    on_progress=lambda pct: progress_calls.append(pct),
                )
        finally:
            mod.subprocess.Popen = original_popen

        self.assertEqual(progress_calls, [10.0, 25.5, 50.0, 100.0])


if __name__ == "__main__":
    unittest.main()
