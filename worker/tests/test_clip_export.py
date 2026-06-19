from pathlib import Path
import sys
from tempfile import NamedTemporaryFile, TemporaryDirectory

import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.export import (
    ClipExportException,
    ClipExportRequest,
    FfmpegClipExportService,
    ProcessExecutionResult,
)
from streamcut_worker.export.service import (
    _SEEK_PREROLL_SEC,
    _FFMPEG_THREAD_CAP,
    _build_reframe_filter,
    REFRAME_CANVAS_WIDTH,
    REFRAME_CANVAS_HEIGHT,
    REFRAME_BLUR_SIGMA,
    REFRAME_BG_DOWNSCALE_WIDTH,
)


class FakeRunner:
    def __init__(self, result: ProcessExecutionResult) -> None:
        self.result = result
        self.commands: list[list[str]] = []

    def run(self, command):
        self.commands.append(list(command))
        return self.result


class ClipExportServiceTests(unittest.TestCase):
    def test_export_builds_expected_command_and_result(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            result = service.export(
                ClipExportRequest(
                    job_id="job-1",
                    candidate_id="7",
                    source_video_path=source_path,
                    start_sec=5.25,
                    end_sec=12.75,
                )
            )

            expected_path = Path(temp_dir) / "artifacts" / "jobs" / "job-1" / "exports" / "candidate-7.mp4"
            self.assertEqual(result.artifact_path, expected_path)
            self.assertEqual(result.status, "COMPLETED")
            self.assertEqual(result.duration_sec, 7.5)
            self.assertEqual(runner.commands[0][0], "ffmpeg")
            self.assertIn("-ss", runner.commands[0])
            self.assertIn("-t", runner.commands[0])
            self.assertIn(str(expected_path), runner.commands[0])
            self.assertTrue(expected_path.parent.exists())

    def test_two_stage_seek_normal_start(self):
        """When start_sec >= PREROLL, coarse = start - PREROLL, fine = PREROLL."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            start_sec = 30.0
            end_sec = 45.0
            service.export(
                ClipExportRequest(
                    job_id="job-2",
                    candidate_id="1",
                    source_video_path=source_path,
                    start_sec=start_sec,
                    end_sec=end_sec,
                )
            )

            cmd = runner.commands[0]
            # Expected coarse = 30 - 10 = 20, fine = 10
            coarse_expected = start_sec - _SEEK_PREROLL_SEC
            fine_expected = _SEEK_PREROLL_SEC

            # Verify arg order: ffmpeg -y -ss <coarse> -i <src> -ss <fine> -t <dur> ...
            self.assertEqual(cmd[0], "ffmpeg")
            self.assertEqual(cmd[1], "-y")
            self.assertEqual(cmd[2], "-ss")
            self.assertEqual(cmd[3], f"{coarse_expected:.6f}")
            self.assertEqual(cmd[4], "-i")
            self.assertEqual(cmd[5], str(source_path))
            self.assertEqual(cmd[6], "-ss")
            self.assertEqual(cmd[7], f"{fine_expected:.6f}")
            self.assertEqual(cmd[8], "-t")
            self.assertEqual(cmd[9], f"{end_sec - start_sec:.6f}")

    def test_two_stage_seek_small_start(self):
        """When start_sec < PREROLL, coarse = 0, fine = start_sec."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            start_sec = 3.5
            end_sec = 8.0
            service.export(
                ClipExportRequest(
                    job_id="job-3",
                    candidate_id="2",
                    source_video_path=source_path,
                    start_sec=start_sec,
                    end_sec=end_sec,
                )
            )

            cmd = runner.commands[0]
            # coarse should be 0 since start < PREROLL
            self.assertEqual(cmd[2], "-ss")
            self.assertEqual(cmd[3], f"{0.0:.6f}")
            self.assertEqual(cmd[4], "-i")
            self.assertEqual(cmd[6], "-ss")
            self.assertEqual(cmd[7], f"{start_sec:.6f}")
            self.assertEqual(cmd[8], "-t")
            self.assertEqual(cmd[9], f"{end_sec - start_sec:.6f}")

    def test_bounded_threads_in_command(self):
        """Command must include -threads with the configured cap."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-4",
                    candidate_id="3",
                    source_video_path=source_path,
                    start_sec=20.0,
                    end_sec=30.0,
                )
            )

            cmd = runner.commands[0]
            threads_idx = cmd.index("-threads")
            self.assertEqual(cmd[threads_idx + 1], str(_FFMPEG_THREAD_CAP))

    def test_avoid_negative_ts_in_command(self):
        """Command must include -avoid_negative_ts make_zero."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-5",
                    candidate_id="4",
                    source_video_path=source_path,
                    start_sec=15.0,
                    end_sec=25.0,
                )
            )

            cmd = runner.commands[0]
            neg_ts_idx = cmd.index("-avoid_negative_ts")
            self.assertEqual(cmd[neg_ts_idx + 1], "make_zero")

    def test_export_raises_useful_error_on_failure(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mkv") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(1, "", "boom"))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            with self.assertRaises(ClipExportException) as ctx:
                service.export(
                    ClipExportRequest(
                        job_id="job-1",
                        candidate_id="7",
                        source_video_path=source_path,
                        start_sec=1.0,
                        end_sec=3.0,
                    )
                )

        self.assertEqual(ctx.exception.returncode, 1)
        self.assertIn("ffmpeg failed", str(ctx.exception))
        self.assertEqual(ctx.exception.stderr, "boom")

    def test_missing_input_file_fails_fast(self):
        runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
        service = FfmpegClipExportService(Path("/tmp/artifacts"), runner)

        with self.assertRaises(ClipExportException) as ctx:
            service.export(
                ClipExportRequest(
                    job_id="job-1",
                    candidate_id="7",
                    source_video_path=Path("missing.mp4"),
                    start_sec=1.0,
                    end_sec=3.0,
                )
            )

        self.assertIn("does not exist", str(ctx.exception))

    def test_invalid_time_range_fails_fast(self):
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            with self.assertRaises(ClipExportException) as ctx:
                service.export(
                    ClipExportRequest(
                        job_id="job-1",
                        candidate_id="7",
                        source_video_path=source_path,
                        start_sec=10.0,
                        end_sec=5.0,
                    )
                )

        self.assertIn("end_sec must be greater", str(ctx.exception))


class VerticalReframeTests(unittest.TestCase):
    """Tests for TASK-093: Tier 0 blurred-fill vertical reframe."""

    def test_reframe_disabled_by_default(self):
        """Default request (vertical_reframe=False) produces no filter_complex."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-r1",
                    candidate_id="1",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=15.0,
                )
            )

            cmd = runner.commands[0]
            self.assertNotIn("-filter_complex", cmd)
            self.assertNotIn("-map", cmd)

    def test_reframe_enabled_injects_filter_complex(self):
        """vertical_reframe=True adds -filter_complex with the blurred-fill graph."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-r2",
                    candidate_id="2",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=15.0,
                    vertical_reframe=True,
                )
            )

            cmd = runner.commands[0]
            fc_idx = cmd.index("-filter_complex")
            filter_graph = cmd[fc_idx + 1]

            # Verify the filter graph structure
            self.assertIn("[0:v]split=2[bg_in][fg_in]", filter_graph)
            self.assertIn("gblur=sigma=", filter_graph)
            self.assertIn("overlay=(W-w)/2:(H-h)/2[out]", filter_graph)

    def test_reframe_maps_output_and_audio(self):
        """vertical_reframe=True maps the [out] pad and original audio."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-r3",
                    candidate_id="3",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=15.0,
                    vertical_reframe=True,
                )
            )

            cmd = runner.commands[0]
            # -map [out] -map 0:a must appear
            map_indices = [i for i, v in enumerate(cmd) if v == "-map"]
            self.assertEqual(len(map_indices), 2)
            self.assertEqual(cmd[map_indices[0] + 1], "[out]")
            self.assertEqual(cmd[map_indices[1] + 1], "0:a")

    def test_reframe_preserves_thread_cap(self):
        """Reframe must not break the TASK-080 bounded threads budget."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-r4",
                    candidate_id="4",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=15.0,
                    vertical_reframe=True,
                )
            )

            cmd = runner.commands[0]
            threads_idx = cmd.index("-threads")
            self.assertEqual(cmd[threads_idx + 1], str(_FFMPEG_THREAD_CAP))

    def test_reframe_preserves_two_stage_seek(self):
        """Reframe must keep the TASK-080 two-stage seek intact."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            start_sec = 30.0
            end_sec = 40.0
            service.export(
                ClipExportRequest(
                    job_id="job-r5",
                    candidate_id="5",
                    source_video_path=source_path,
                    start_sec=start_sec,
                    end_sec=end_sec,
                    vertical_reframe=True,
                )
            )

            cmd = runner.commands[0]
            coarse_expected = start_sec - _SEEK_PREROLL_SEC
            fine_expected = _SEEK_PREROLL_SEC

            self.assertEqual(cmd[2], "-ss")
            self.assertEqual(cmd[3], f"{coarse_expected:.6f}")
            self.assertEqual(cmd[4], "-i")
            self.assertEqual(cmd[6], "-ss")
            self.assertEqual(cmd[7], f"{fine_expected:.6f}")

    def test_build_reframe_filter_defaults(self):
        """_build_reframe_filter with defaults produces the expected graph."""
        graph = _build_reframe_filter()
        bg_downscale_h = int(REFRAME_BG_DOWNSCALE_WIDTH * REFRAME_CANVAS_HEIGHT / REFRAME_CANVAS_WIDTH)

        # Background: downscale -> crop -> blur -> scale to canvas
        self.assertIn(f"scale={REFRAME_BG_DOWNSCALE_WIDTH}:{bg_downscale_h}", graph)
        self.assertIn(f"gblur=sigma={REFRAME_BLUR_SIGMA}", graph)
        self.assertIn(f"scale={REFRAME_CANVAS_WIDTH}:{REFRAME_CANVAS_HEIGHT}[bg]", graph)

        # Foreground: scale to canvas width
        self.assertIn(f"scale={REFRAME_CANVAS_WIDTH}:-2", graph)

        # Overlay centered
        self.assertIn("overlay=(W-w)/2:(H-h)/2[out]", graph)

    def test_build_reframe_filter_custom_values(self):
        """_build_reframe_filter respects custom canvas/blur parameters."""
        graph = _build_reframe_filter(
            canvas_w=720,
            canvas_h=1280,
            blur_sigma=20,
            bg_downscale_w=180,
        )
        bg_h = int(180 * 1280 / 720)

        self.assertIn("scale=180:", graph)
        self.assertIn(f"crop=180:{bg_h}", graph)
        self.assertIn("gblur=sigma=20", graph)
        self.assertIn("scale=720:1280[bg]", graph)
        self.assertIn("scale=720:-2", graph)

    def test_build_reframe_filter_blurs_downscaled_not_fullres(self):
        """Blur is applied to the downscaled background, not full-res (budget)."""
        graph = _build_reframe_filter()
        # The graph must downscale BEFORE blur: scale(small) -> crop -> gblur -> scale(canvas)
        # Check ordering: bg_in scale appears before gblur, and gblur before final scale
        bg_chain_start = graph.index("[bg_in]")
        blur_pos = graph.index("gblur=sigma=")
        final_scale_pos = graph.index(f"scale={REFRAME_CANVAS_WIDTH}:{REFRAME_CANVAS_HEIGHT}[bg]")
        downscale_pos = graph.index(f"scale={REFRAME_BG_DOWNSCALE_WIDTH}:")

        self.assertLess(downscale_pos, blur_pos, "downscale must precede blur")
        self.assertLess(blur_pos, final_scale_pos, "blur must precede upscale to canvas")
        self.assertGreater(downscale_pos, bg_chain_start, "downscale is in the bg chain")


if __name__ == "__main__":
    unittest.main()
