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
    WordTiming,
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
from streamcut_worker.export.subtitles import (
    generate_ass_content,
    write_ass_file,
    _offset_words_to_clip,
    _group_into_phrases,
    _build_karaoke_text,
    _format_ass_time,
    _ClipWord,
    _Phrase,
    CAPTION_MARGIN_V,
    CAPTION_MARGIN_V_REFRAME,
    MAX_WORDS_PER_PHRASE,
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


class SubtitleOffsetTests(unittest.TestCase):
    """Tests for clip-relative word offset and filtering."""

    def test_words_offset_to_clip_start(self):
        """Word timestamps are shifted so clip start = 0."""
        words = [
            WordTiming("hello", 10.0, 10.5),
            WordTiming("world", 10.5, 11.0),
        ]
        result = _offset_words_to_clip(words, clip_start=10.0, clip_end=12.0)
        self.assertEqual(len(result), 2)
        self.assertAlmostEqual(result[0].start_sec, 0.0)
        self.assertAlmostEqual(result[0].end_sec, 0.5)
        self.assertAlmostEqual(result[1].start_sec, 0.5)
        self.assertAlmostEqual(result[1].end_sec, 1.0)

    def test_words_outside_clip_are_excluded(self):
        """Words entirely outside [clip_start, clip_end] are dropped."""
        words = [
            WordTiming("before", 5.0, 6.0),
            WordTiming("inside", 10.5, 11.0),
            WordTiming("after", 15.0, 16.0),
        ]
        result = _offset_words_to_clip(words, clip_start=10.0, clip_end=12.0)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].word, "inside")

    def test_words_partially_overlapping_are_clamped(self):
        """Words that straddle clip boundaries are clamped, not dropped."""
        words = [
            WordTiming("straddle_start", 9.5, 10.5),
            WordTiming("straddle_end", 11.5, 12.5),
        ]
        result = _offset_words_to_clip(words, clip_start=10.0, clip_end=12.0)
        self.assertEqual(len(result), 2)
        # First word: clamped start at clip_start -> offset 0.0, end at 0.5
        self.assertAlmostEqual(result[0].start_sec, 0.0)
        self.assertAlmostEqual(result[0].end_sec, 0.5)
        # Second word: starts at 1.5, clamped end at clip_end -> 2.0
        self.assertAlmostEqual(result[1].start_sec, 1.5)
        self.assertAlmostEqual(result[1].end_sec, 2.0)

    def test_empty_words_returns_empty(self):
        result = _offset_words_to_clip([], clip_start=0.0, clip_end=10.0)
        self.assertEqual(result, [])


class SubtitlePhraseGroupingTests(unittest.TestCase):
    """Tests for grouping words into readable phrases."""

    def test_groups_words_up_to_max(self):
        """Words are grouped into chunks of MAX_WORDS_PER_PHRASE."""
        words = [
            _ClipWord(f"w{i}", float(i), float(i + 1))
            for i in range(12)
        ]
        phrases = _group_into_phrases(words, max_per_phrase=5)
        self.assertEqual(len(phrases), 3)
        self.assertEqual(len(phrases[0].words), 5)
        self.assertEqual(len(phrases[1].words), 5)
        self.assertEqual(len(phrases[2].words), 2)

    def test_phrase_timing_spans_its_words(self):
        """Phrase start/end matches first/last word."""
        words = [
            _ClipWord("a", 1.0, 1.5),
            _ClipWord("b", 1.5, 2.0),
            _ClipWord("c", 2.0, 2.5),
        ]
        phrases = _group_into_phrases(words, max_per_phrase=5)
        self.assertEqual(len(phrases), 1)
        self.assertAlmostEqual(phrases[0].start_sec, 1.0)
        self.assertAlmostEqual(phrases[0].end_sec, 2.5)

    def test_single_word_forms_its_own_phrase(self):
        words = [_ClipWord("only", 0.0, 0.5)]
        phrases = _group_into_phrases(words, max_per_phrase=5)
        self.assertEqual(len(phrases), 1)
        self.assertEqual(len(phrases[0].words), 1)

    def test_empty_words_returns_empty_phrases(self):
        phrases = _group_into_phrases([], max_per_phrase=5)
        self.assertEqual(phrases, [])


class SubtitleKaraokeTimingTests(unittest.TestCase):
    """Tests for ASS karaoke tag generation."""

    def test_karaoke_text_has_kf_tags(self):
        r"""Each word gets a \kf<cs> prefix."""
        phrase = _Phrase(
            words=[
                _ClipWord("hello", 0.0, 0.5),
                _ClipWord("world", 0.5, 1.2),
            ],
            start_sec=0.0,
            end_sec=1.2,
        )
        text = _build_karaoke_text(phrase)
        self.assertIn(r"{\kf50}hello", text)
        self.assertIn(r"{\kf70}world", text)

    def test_karaoke_minimum_duration(self):
        r"""Very short words still get \kf1 (1 centisecond minimum)."""
        phrase = _Phrase(
            words=[_ClipWord("x", 0.0, 0.001)],
            start_sec=0.0,
            end_sec=0.001,
        )
        text = _build_karaoke_text(phrase)
        self.assertIn(r"{\kf1}x", text)


class SubtitleASSGenerationTests(unittest.TestCase):
    """Tests for full ASS content generation."""

    def _make_words(self) -> list[WordTiming]:
        """Create test words spanning absolute time 100.0..103.5."""
        return [
            WordTiming("This", 100.0, 100.3),
            WordTiming("is", 100.3, 100.5),
            WordTiming("a", 100.5, 100.6),
            WordTiming("test", 100.6, 101.0),
            WordTiming("of", 101.0, 101.2),
            WordTiming("the", 101.2, 101.4),
            WordTiming("karaoke", 101.4, 102.0),
            WordTiming("subtitle", 102.0, 102.5),
            WordTiming("system", 102.5, 103.0),
            WordTiming("now", 103.0, 103.5),
        ]

    def test_generates_valid_ass_structure(self):
        """Output contains ASS header, styles, and events."""
        content = generate_ass_content(
            self._make_words(), clip_start=100.0, clip_end=104.0,
        )
        self.assertIsNotNone(content)
        self.assertIn("[Script Info]", content)
        self.assertIn("[V4+ Styles]", content)
        self.assertIn("[Events]", content)
        self.assertIn("Style: Karaoke", content)
        self.assertIn("Dialogue:", content)

    def test_generates_clip_relative_timestamps(self):
        """Dialogue timestamps are relative to clip start (0-based)."""
        content = generate_ass_content(
            self._make_words(), clip_start=100.0, clip_end=104.0,
        )
        self.assertIsNotNone(content)
        # First phrase starts at 0:00:00.00 (clip-relative)
        self.assertIn("0:00:00.00", content)
        # Should NOT contain the absolute time 100+ seconds
        self.assertNotIn("0:01:40", content)

    def test_returns_none_when_no_words(self):
        """Graceful no-op when word list is empty."""
        content = generate_ass_content([], clip_start=0.0, clip_end=10.0)
        self.assertIsNone(content)

    def test_returns_none_when_no_words_in_clip(self):
        """Graceful no-op when words exist but none fall within the clip."""
        words = [WordTiming("outside", 50.0, 51.0)]
        content = generate_ass_content(words, clip_start=100.0, clip_end=110.0)
        self.assertIsNone(content)

    def test_karaoke_tags_present_in_dialogue(self):
        r"""Dialogue text includes \kf karaoke tags."""
        content = generate_ass_content(
            self._make_words(), clip_start=100.0, clip_end=104.0,
        )
        self.assertIsNotNone(content)
        self.assertIn(r"{\kf", content)

    def test_phrase_grouping_creates_multiple_events(self):
        """10 words with max_per_phrase=5 should create 2 Dialogue events."""
        content = generate_ass_content(
            self._make_words(), clip_start=100.0, clip_end=104.0,
            max_words_per_phrase=5,
        )
        self.assertIsNotNone(content)
        dialogue_count = content.count("Dialogue:")
        self.assertEqual(dialogue_count, 2)

    def test_reframe_mode_increases_margin(self):
        """vertical_reframe=True uses the higher MarginV."""
        content_normal = generate_ass_content(
            self._make_words(), clip_start=100.0, clip_end=104.0,
        )
        content_reframe = generate_ass_content(
            self._make_words(), clip_start=100.0, clip_end=104.0,
            vertical_reframe=True,
        )
        self.assertIsNotNone(content_normal)
        self.assertIsNotNone(content_reframe)
        # The MarginV value in the Style line should differ
        self.assertIn(f",{CAPTION_MARGIN_V},1", content_normal)
        self.assertIn(f",{CAPTION_MARGIN_V_REFRAME},1", content_reframe)


class SubtitleASSFileTests(unittest.TestCase):
    """Tests for write_ass_file."""

    def test_writes_ass_file_to_disk(self):
        with TemporaryDirectory() as tmp:
            words = [WordTiming("hello", 5.0, 5.5), WordTiming("world", 5.5, 6.0)]
            out = Path(tmp) / "subs.ass"
            result = write_ass_file(words, 5.0, 7.0, out)
            self.assertIsNotNone(result)
            self.assertTrue(out.exists())
            content = out.read_text(encoding="utf-8")
            self.assertIn("[Script Info]", content)

    def test_returns_none_when_no_words(self):
        with TemporaryDirectory() as tmp:
            out = Path(tmp) / "subs.ass"
            result = write_ass_file([], 0.0, 10.0, out)
            self.assertIsNone(result)
            self.assertFalse(out.exists())


class SubtitleASSTimeFormatTests(unittest.TestCase):
    """Tests for ASS time formatting."""

    def test_zero(self):
        self.assertEqual(_format_ass_time(0.0), "0:00:00.00")

    def test_simple_seconds(self):
        self.assertEqual(_format_ass_time(5.5), "0:00:05.50")

    def test_minutes(self):
        self.assertEqual(_format_ass_time(65.25), "0:01:05.25")

    def test_hours(self):
        self.assertEqual(_format_ass_time(3661.0), "1:01:01.00")


class SubtitleExportIntegrationTests(unittest.TestCase):
    """Tests for subtitle burn integration with the ffmpeg export command (mocked)."""

    def _make_words(self, start: float = 5.0, end: float = 10.0) -> list[WordTiming]:
        """Create words spanning the given absolute range."""
        duration = end - start
        word_dur = duration / 4
        return [
            WordTiming("one", start, start + word_dur),
            WordTiming("two", start + word_dur, start + 2 * word_dur),
            WordTiming("three", start + 2 * word_dur, start + 3 * word_dur),
            WordTiming("four", start + 3 * word_dur, start + 4 * word_dur),
        ]

    def test_captions_add_vf_ass_filter(self):
        """With words and captions_enabled, -vf ass=<path> appears in the command."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-sub1",
                    candidate_id="1",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=10.0,
                    words=self._make_words(5.0, 10.0),
                )
            )

            cmd = runner.commands[0]
            self.assertIn("-vf", cmd)
            vf_idx = cmd.index("-vf")
            self.assertIn("ass=", cmd[vf_idx + 1])

    def test_captions_disabled_no_filter(self):
        """captions_enabled=False means no subtitle filter even with words."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-sub2",
                    candidate_id="2",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=10.0,
                    captions_enabled=False,
                    words=self._make_words(5.0, 10.0),
                )
            )

            cmd = runner.commands[0]
            self.assertNotIn("-vf", cmd)
            # Should also not have filter_complex for subs
            if "-filter_complex" in cmd:
                fc_idx = cmd.index("-filter_complex")
                self.assertNotIn("ass=", cmd[fc_idx + 1])

    def test_no_words_graceful_noop(self):
        """Export without words succeeds without any subtitle filter."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            result = service.export(
                ClipExportRequest(
                    job_id="job-sub3",
                    candidate_id="3",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=10.0,
                    words=[],
                )
            )

            cmd = runner.commands[0]
            self.assertNotIn("-vf", cmd)
            self.assertNotIn("-filter_complex", cmd)
            self.assertEqual(result.status, "COMPLETED")

    def test_captions_with_reframe_compose_filter_complex(self):
        """With both reframe and captions, filter_complex contains reframe + ass."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-sub4",
                    candidate_id="4",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=10.0,
                    vertical_reframe=True,
                    words=self._make_words(5.0, 10.0),
                )
            )

            cmd = runner.commands[0]
            fc_idx = cmd.index("-filter_complex")
            graph = cmd[fc_idx + 1]

            # Reframe graph is present
            self.assertIn("[0:v]split=2[bg_in][fg_in]", graph)
            self.assertIn("gblur=sigma=", graph)
            # Reframe output goes to [reframed], then ass filter produces [out]
            self.assertIn("[reframed]", graph)
            self.assertIn("ass=", graph)
            self.assertIn("[out]", graph)

            # -map [out] -map 0:a still present
            map_indices = [i for i, v in enumerate(cmd) if v == "-map"]
            self.assertEqual(len(map_indices), 2)
            self.assertEqual(cmd[map_indices[0] + 1], "[out]")
            self.assertEqual(cmd[map_indices[1] + 1], "0:a")

    def test_captions_preserve_thread_cap(self):
        """Subtitle burn must not break the TASK-080 bounded threads budget."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-sub5",
                    candidate_id="5",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=10.0,
                    words=self._make_words(5.0, 10.0),
                )
            )

            cmd = runner.commands[0]
            threads_idx = cmd.index("-threads")
            self.assertEqual(cmd[threads_idx + 1], str(_FFMPEG_THREAD_CAP))

    def test_captions_preserve_two_stage_seek(self):
        """Subtitle burn must keep the TASK-080 two-stage seek intact."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            start_sec = 30.0
            end_sec = 40.0
            service.export(
                ClipExportRequest(
                    job_id="job-sub6",
                    candidate_id="6",
                    source_video_path=source_path,
                    start_sec=start_sec,
                    end_sec=end_sec,
                    words=self._make_words(start_sec, end_sec),
                )
            )

            cmd = runner.commands[0]
            coarse_expected = start_sec - _SEEK_PREROLL_SEC
            fine_expected = _SEEK_PREROLL_SEC
            self.assertEqual(cmd[2], "-ss")
            self.assertEqual(cmd[3], f"{coarse_expected:.6f}")
            self.assertEqual(cmd[6], "-ss")
            self.assertEqual(cmd[7], f"{fine_expected:.6f}")

    def test_ass_file_created_alongside_artifact(self):
        """The ASS file is written next to the output MP4."""
        with TemporaryDirectory() as temp_dir, NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
            tmp.write(b"dummy")
            source_path = Path(tmp.name)
            runner = FakeRunner(ProcessExecutionResult(0, "ok", ""))
            service = FfmpegClipExportService(Path(temp_dir) / "artifacts", runner)

            service.export(
                ClipExportRequest(
                    job_id="job-sub7",
                    candidate_id="7",
                    source_video_path=source_path,
                    start_sec=5.0,
                    end_sec=10.0,
                    words=self._make_words(5.0, 10.0),
                )
            )

            expected_ass = Path(temp_dir) / "artifacts" / "jobs" / "job-sub7" / "exports" / "candidate-7.ass"
            self.assertTrue(expected_ass.exists())
            content = expected_ass.read_text(encoding="utf-8")
            self.assertIn("[Script Info]", content)
            self.assertIn(r"{\kf", content)


if __name__ == "__main__":
    unittest.main()
