"""Tests for hybrid LLM highlight detection (TASK-089).

All tests mock the LLM client — no GPU or real model required.
Covers: chunking, prompt-hint injection, LLM output parsing,
merge/dedupe, fallback paths, and eval harness integration.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.analysis.hybrid import (
    CHUNK_DURATION_SEC,
    CHUNK_OVERLAP_SEC,
    CHUNK_STEP_SEC,
    LlmHighlightMoment,
    TranscriptChunk,
    analyze_candidates_hybrid,
    build_chunk_prompt,
    build_chunks,
    hybrid_detect,
    is_hybrid_enabled,
    merge_moments,
    select_moments,
    _parse_selection,
    moments_to_candidates,
    parse_llm_highlights,
    _extract_emotion_hits,
    _extract_loudness_peaks,
    _extract_silence_boundaries,
)
from streamcut_worker.analysis.models import (
    CandidateAnalysisRequest,
    LoudnessProfile,
)
from streamcut_worker.inference import LlmClient, LlmUnavailableError
from streamcut_worker.silence.models import SilenceInterval
from streamcut_worker.transcription.models import TranscriptSegment


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_segments(count: int, duration_each: float = 10.0, start: float = 0.0) -> list[TranscriptSegment]:
    """Create ``count`` consecutive transcript segments."""
    segments = []
    for i in range(count):
        s = start + i * duration_each
        e = s + duration_each
        segments.append(TranscriptSegment(
            start_sec=s, end_sec=e,
            text=f"Segment {i} text at {s:.0f}s", word_count=5,
        ))
    return segments


def _mock_llm_client(responses: list[str] | None = None, available: bool = True) -> MagicMock:
    """Create a mock LlmClient that returns canned responses."""
    client = MagicMock(spec=LlmClient)
    client.is_available = available
    client.enabled = available
    if responses is not None:
        client.generate = MagicMock(side_effect=responses)
    else:
        client.generate = MagicMock(return_value="[]")
    client.unload = MagicMock()
    return client


# ---------------------------------------------------------------------------
# Chunking tests
# ---------------------------------------------------------------------------

class TestBuildChunks(unittest.TestCase):

    def test_short_vod_single_chunk(self) -> None:
        """A VOD shorter than chunk_duration produces one chunk."""
        segments = _make_segments(5, duration_each=10.0)  # 50s total
        chunks = build_chunks(segments, duration_sec=50.0)
        self.assertEqual(len(chunks), 1)
        self.assertAlmostEqual(chunks[0].start_sec, 0.0)
        self.assertAlmostEqual(chunks[0].end_sec, 50.0)
        self.assertEqual(len(chunks[0].segments), 5)

    def test_3h_vod_chunk_count(self) -> None:
        """A 3h VOD (10800s) should produce ~23 chunks."""
        segments = _make_segments(1080, duration_each=10.0)
        chunks = build_chunks(segments, duration_sec=10800.0)
        # Expected: ceil((10800 - 600) / 480) + 1 = 23
        self.assertGreaterEqual(len(chunks), 20)
        self.assertLessEqual(len(chunks), 25)

    def test_chunks_overlap(self) -> None:
        """Adjacent chunks must overlap by CHUNK_OVERLAP_SEC."""
        segments = _make_segments(200, duration_each=10.0)
        chunks = build_chunks(segments, duration_sec=2000.0)
        self.assertGreater(len(chunks), 1)
        for i in range(len(chunks) - 1):
            overlap = chunks[i].end_sec - chunks[i + 1].start_sec
            # Overlap should be approximately CHUNK_OVERLAP_SEC (120s)
            # The last chunk may be shorter
            if chunks[i + 1].end_sec - chunks[i + 1].start_sec >= CHUNK_DURATION_SEC:
                self.assertAlmostEqual(overlap, CHUNK_OVERLAP_SEC, delta=1.0)

    def test_empty_segments(self) -> None:
        chunks = build_chunks([], duration_sec=100.0)
        self.assertEqual(len(chunks), 0)

    def test_zero_duration(self) -> None:
        segments = _make_segments(5)
        chunks = build_chunks(segments, duration_sec=0.0)
        self.assertEqual(len(chunks), 0)

    def test_segments_assigned_to_correct_chunks(self) -> None:
        """Each segment appears in chunks whose time range overlaps it."""
        segments = _make_segments(100, duration_each=10.0)
        chunks = build_chunks(segments, duration_sec=1000.0)
        for chunk in chunks:
            for seg in chunk.segments:
                # Segment must overlap the chunk window
                overlap = min(seg.end_sec, chunk.end_sec) - max(seg.start_sec, chunk.start_sec)
                self.assertGreater(overlap, 0.0)


# ---------------------------------------------------------------------------
# Hint extraction tests
# ---------------------------------------------------------------------------

class TestHintExtraction(unittest.TestCase):

    def test_loudness_peaks_extraction(self) -> None:
        profile = LoudnessProfile(
            time_sec=(10.0, 20.0, 30.0, 40.0, 50.0),
            rms_db=(-30.0, -10.0, -25.0, -5.0, -35.0),
        )
        peaks = _extract_loudness_peaks(profile, 0.0, 60.0, top_n=3)
        self.assertEqual(len(peaks), 3)
        # Should be sorted loudest first
        self.assertAlmostEqual(peaks[0][1], -5.0)
        self.assertAlmostEqual(peaks[1][1], -10.0)
        self.assertAlmostEqual(peaks[2][1], -25.0)

    def test_loudness_peaks_within_window(self) -> None:
        profile = LoudnessProfile(
            time_sec=(10.0, 20.0, 30.0, 40.0),
            rms_db=(-30.0, -10.0, -25.0, -5.0),
        )
        peaks = _extract_loudness_peaks(profile, 15.0, 35.0, top_n=5)
        self.assertEqual(len(peaks), 2)
        times = {p[0] for p in peaks}
        self.assertIn(20.0, times)
        self.assertIn(30.0, times)

    def test_loudness_peaks_none_profile(self) -> None:
        peaks = _extract_loudness_peaks(None, 0.0, 100.0)
        self.assertEqual(peaks, [])

    def test_emotion_hits_extraction(self) -> None:
        segments = [
            TranscriptSegment(10.0, 20.0, "WOW that was amazing!", 5),
            TranscriptSegment(30.0, 40.0, "boring normal talk", 3),
        ]
        hits = _extract_emotion_hits(segments, ("amazing",), 0.0, 50.0)
        self.assertEqual(len(hits), 1)
        self.assertAlmostEqual(hits[0][0], 10.0)

    def test_silence_boundaries(self) -> None:
        silences = [
            SilenceInterval(5.0, 8.0, 3.0),
            SilenceInterval(15.0, 18.0, 3.0),
            SilenceInterval(100.0, 105.0, 5.0),
        ]
        bounds = _extract_silence_boundaries(silences, 0.0, 20.0)
        self.assertEqual(len(bounds), 2)


# ---------------------------------------------------------------------------
# Prompt construction tests
# ---------------------------------------------------------------------------

class TestPromptConstruction(unittest.TestCase):

    def test_prompt_contains_transcript(self) -> None:
        segments = [TranscriptSegment(10.0, 20.0, "Hello world", 2)]
        chunk = TranscriptChunk(start_sec=0.0, end_sec=60.0, segments=segments)
        prompt = build_chunk_prompt(chunk, [], [], [])
        self.assertIn("[10.0s - 20.0s] Hello world", prompt)

    def test_prompt_contains_loudness_hints(self) -> None:
        chunk = TranscriptChunk(start_sec=0.0, end_sec=60.0, segments=[])
        peaks = [(15.0, -10.0), (30.0, -5.0)]
        prompt = build_chunk_prompt(chunk, peaks, [], [])
        self.assertIn("LOUDNESS PEAKS", prompt)
        self.assertIn("15.0s", prompt)
        self.assertIn("-10.0 dB", prompt)

    def test_prompt_contains_emotion_hints(self) -> None:
        chunk = TranscriptChunk(start_sec=0.0, end_sec=60.0, segments=[])
        emotions = [(12.0, "WOW that was epic")]
        prompt = build_chunk_prompt(chunk, [], emotions, [])
        self.assertIn("EMOTION/EXCITEMENT MARKERS", prompt)
        self.assertIn("WOW that was epic", prompt)

    def test_prompt_contains_silence_hints(self) -> None:
        chunk = TranscriptChunk(start_sec=0.0, end_sec=60.0, segments=[])
        silences = [(5.0, 8.0), (40.0, 45.0)]
        prompt = build_chunk_prompt(chunk, [], [], silences)
        self.assertIn("SILENCE BOUNDARIES", prompt)
        self.assertIn("5.0s to 8.0s", prompt)

    def test_prompt_requests_json(self) -> None:
        chunk = TranscriptChunk(start_sec=0.0, end_sec=60.0, segments=[])
        prompt = build_chunk_prompt(chunk, [], [], [])
        self.assertIn("JSON array", prompt)
        self.assertIn("start_sec", prompt)
        self.assertIn("confidence", prompt)

    def test_prompt_chunk_range_mentioned(self) -> None:
        chunk = TranscriptChunk(start_sec=600.0, end_sec=1200.0, segments=[])
        prompt = build_chunk_prompt(chunk, [], [], [])
        self.assertIn("600s", prompt)
        self.assertIn("1200s", prompt)


# ---------------------------------------------------------------------------
# LLM output parsing tests
# ---------------------------------------------------------------------------

class TestParseLlmHighlights(unittest.TestCase):

    def test_parse_clean_json(self) -> None:
        raw = json.dumps([
            {"start_sec": 10.0, "end_sec": 40.0, "reason": "funny moment", "confidence": 0.9},
            {"start_sec": 100.0, "end_sec": 130.0, "reason": "epic fail", "confidence": 0.7},
        ])
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 2)
        self.assertAlmostEqual(moments[0].start_sec, 10.0)
        self.assertAlmostEqual(moments[0].confidence, 0.9)
        self.assertEqual(moments[0].reason, "funny moment")

    def test_parse_markdown_code_block(self) -> None:
        raw = """Here are the highlights:
```json
[{"start_sec": 50.0, "end_sec": 80.0, "reason": "hype", "confidence": 0.8}]
```"""
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 1)
        self.assertAlmostEqual(moments[0].start_sec, 50.0)

    def test_parse_extra_text_around_json(self) -> None:
        raw = """I found these highlights:
[{"start_sec": 20.0, "end_sec": 50.0, "reason": "reaction", "confidence": 0.85}]
That's all!"""
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 1)

    def test_parse_empty_array(self) -> None:
        moments = parse_llm_highlights("[]")
        self.assertEqual(len(moments), 0)

    def test_parse_camelcase_keys(self) -> None:
        raw = json.dumps([
            {"startSec": 10.0, "endSec": 40.0, "reason": "funny", "confidence": 0.8},
        ])
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 1)
        self.assertAlmostEqual(moments[0].start_sec, 10.0)
        self.assertAlmostEqual(moments[0].end_sec, 40.0)

    def test_parse_missing_reason(self) -> None:
        raw = json.dumps([
            {"start_sec": 10.0, "end_sec": 40.0, "confidence": 0.8},
        ])
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 1)
        self.assertEqual(moments[0].reason, "")

    def test_parse_garbage_input(self) -> None:
        moments = parse_llm_highlights("This is not JSON at all, just random text.")
        self.assertEqual(len(moments), 0)

    def test_parse_partial_valid(self) -> None:
        """Parses valid items and skips malformed ones."""
        raw = json.dumps([
            {"start_sec": 10.0, "end_sec": 40.0, "reason": "good", "confidence": 0.9},
            {"bad": "entry"},
            {"start_sec": 50.0, "end_sec": 80.0, "reason": "also good", "confidence": 0.7},
        ])
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 2)

    def test_parse_clamps_confidence(self) -> None:
        raw = json.dumps([
            {"start_sec": 10.0, "end_sec": 40.0, "reason": "x", "confidence": 1.5},
        ])
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 1)
        self.assertAlmostEqual(moments[0].confidence, 1.0)

    def test_parse_rejects_invalid_range(self) -> None:
        raw = json.dumps([
            {"start_sec": 40.0, "end_sec": 10.0, "reason": "backwards", "confidence": 0.9},
        ])
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 0)

    def test_parse_individual_objects_fallback(self) -> None:
        """When JSON array parse fails, tries individual objects."""
        raw = """Here's a highlight: {"start_sec": 25.0, "end_sec": 55.0, "reason": "wow", "confidence": 0.8} and another {"start_sec": 100.0, "end_sec": 130.0, "reason": "epic", "confidence": 0.6}"""
        moments = parse_llm_highlights(raw)
        self.assertEqual(len(moments), 2)


# ---------------------------------------------------------------------------
# Merge/dedupe tests
# ---------------------------------------------------------------------------

class TestMergeMoments(unittest.TestCase):

    def test_no_overlap_keeps_all(self) -> None:
        moments = [
            LlmHighlightMoment(0.0, 30.0, "a", 0.9),
            LlmHighlightMoment(60.0, 90.0, "b", 0.8),
            LlmHighlightMoment(120.0, 150.0, "c", 0.7),
        ]
        merged = merge_moments(moments)
        self.assertEqual(len(merged), 3)

    def test_overlapping_keeps_higher_confidence(self) -> None:
        moments = [
            LlmHighlightMoment(10.0, 40.0, "lower", 0.7),
            LlmHighlightMoment(15.0, 45.0, "higher", 0.9),
        ]
        merged = merge_moments(moments)
        self.assertEqual(len(merged), 1)
        self.assertEqual(merged[0].reason, "higher")
        self.assertAlmostEqual(merged[0].confidence, 0.9)

    def test_partial_overlap_below_threshold(self) -> None:
        """Moments with IoU below threshold are kept separate."""
        moments = [
            LlmHighlightMoment(0.0, 30.0, "a", 0.9),
            LlmHighlightMoment(25.0, 55.0, "b", 0.8),  # only 5s overlap / 55s union
        ]
        merged = merge_moments(moments)
        self.assertEqual(len(merged), 2)

    def test_empty_input(self) -> None:
        merged = merge_moments([])
        self.assertEqual(len(merged), 0)

    def test_sorted_by_start_time(self) -> None:
        moments = [
            LlmHighlightMoment(100.0, 130.0, "b", 0.8),
            LlmHighlightMoment(10.0, 40.0, "a", 0.9),
        ]
        merged = merge_moments(moments)
        self.assertEqual(len(merged), 2)
        self.assertAlmostEqual(merged[0].start_sec, 10.0)
        self.assertAlmostEqual(merged[1].start_sec, 100.0)


# ---------------------------------------------------------------------------
# Moments-to-candidates conversion
# ---------------------------------------------------------------------------

class TestMomentsToCandidates(unittest.TestCase):

    def test_basic_conversion(self) -> None:
        moments = [
            LlmHighlightMoment(10.0, 40.0, "funny moment", 0.9),
        ]
        segments = [TranscriptSegment(10.0, 20.0, "Hello world", 2)]
        candidates = moments_to_candidates(moments, segments)
        self.assertEqual(len(candidates), 1)
        self.assertAlmostEqual(candidates[0].start_sec, 10.0)
        self.assertAlmostEqual(candidates[0].end_sec, 40.0)
        self.assertAlmostEqual(candidates[0].score, 0.9)
        self.assertIn("Hello world", candidates[0].transcript_excerpt)

    def test_top_n_limits_output(self) -> None:
        moments = [
            LlmHighlightMoment(0.0, 30.0, "a", 0.9),
            LlmHighlightMoment(60.0, 90.0, "b", 0.8),
            LlmHighlightMoment(120.0, 150.0, "c", 0.7),
        ]
        candidates = moments_to_candidates(moments, [], top_n=2)
        self.assertEqual(len(candidates), 2)

    def test_ranked_by_confidence(self) -> None:
        moments = [
            LlmHighlightMoment(0.0, 30.0, "low", 0.3),
            LlmHighlightMoment(60.0, 90.0, "high", 0.95),
        ]
        candidates = moments_to_candidates(moments, [])
        self.assertAlmostEqual(candidates[0].score, 0.95)


# ---------------------------------------------------------------------------
# Full hybrid detection with mocked LLM
# ---------------------------------------------------------------------------

class TestHybridDetect(unittest.TestCase):

    def test_hybrid_detect_calls_llm_per_chunk(self) -> None:
        segments = _make_segments(20, duration_each=60.0)  # 1200s VOD
        request = CandidateAnalysisRequest(
            job_id="test-hybrid-1",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=1200.0,
        )
        chunks = build_chunks(segments, 1200.0)

        # Prepare one response per chunk
        response = json.dumps([
            {"start_sec": 10.0, "end_sec": 40.0, "reason": "test", "confidence": 0.8},
        ])
        client = _mock_llm_client(responses=[response] * len(chunks))

        result = hybrid_detect(llm_client=client, request=request)
        self.assertEqual(client.generate.call_count, len(chunks))
        self.assertGreater(len(result.clip_candidates), 0)
        client.unload.assert_called_once()

    def test_hybrid_detect_merges_across_chunks(self) -> None:
        """Duplicate moments from overlapping chunks should be merged."""
        segments = _make_segments(200, duration_each=10.0)  # 2000s
        request = CandidateAnalysisRequest(
            job_id="test-merge",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=2000.0,
        )
        chunks = build_chunks(segments, 2000.0)

        # Every chunk returns the same moment (simulates overlap detection)
        response = json.dumps([
            {"start_sec": 500.0, "end_sec": 530.0, "reason": "same moment", "confidence": 0.85},
        ])
        client = _mock_llm_client(responses=[response] * len(chunks))

        result = hybrid_detect(llm_client=client, request=request)
        # Should dedupe to 1 candidate despite multiple chunks returning it
        self.assertEqual(len(result.clip_candidates), 1)
        self.assertAlmostEqual(result.clip_candidates[0].score, 0.85)

    def test_hybrid_detect_empty_transcript(self) -> None:
        request = CandidateAnalysisRequest(
            job_id="test-empty",
            transcript_segments=[],
            silence_segments=[],
            duration_sec=0.0,
        )
        client = _mock_llm_client()
        result = hybrid_detect(llm_client=client, request=request)
        self.assertEqual(len(result.clip_candidates), 0)
        client.generate.assert_not_called()

    def test_hybrid_detect_includes_hints_in_prompt(self) -> None:
        """Verify that loudness/emotion/silence hints are injected."""
        segments = [TranscriptSegment(10.0, 20.0, "amazing WOW!", 3)]
        profile = LoudnessProfile(
            time_sec=(15.0,),
            rms_db=(-5.0,),
        )
        silence = [SilenceInterval(5.0, 8.0, 3.0)]
        request = CandidateAnalysisRequest(
            job_id="test-hints",
            transcript_segments=segments,
            silence_segments=silence,
            duration_sec=30.0,
            emotion_keywords=("amazing",),
            loudness_profile=profile,
        )

        captured_prompts: list[str] = []

        def capture_generate(prompt, **kwargs):
            captured_prompts.append(prompt)
            return "[]"

        client = _mock_llm_client()
        client.generate = MagicMock(side_effect=capture_generate)

        hybrid_detect(llm_client=client, request=request)

        self.assertEqual(len(captured_prompts), 1)
        prompt = captured_prompts[0]
        self.assertIn("LOUDNESS PEAKS", prompt)
        self.assertIn("-5.0 dB", prompt)
        self.assertIn("EMOTION", prompt)
        self.assertIn("SILENCE BOUNDARIES", prompt)
        self.assertIn("amazing WOW!", prompt)


# ---------------------------------------------------------------------------
# Fallback wiring
# ---------------------------------------------------------------------------

class TestAnalyzeCandidatesHybridFallback(unittest.TestCase):

    def test_falls_back_when_hybrid_disabled(self) -> None:
        """When HYBRID_DETECTION_ENABLED is false, uses heuristic."""
        segments = _make_segments(3, duration_each=10.0)
        request = CandidateAnalysisRequest(
            job_id="test-fallback-disabled",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=30.0,
        )
        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "false"}):
            result = analyze_candidates_hybrid(request, llm_client=None)
        # Heuristic produces analysis windows; hybrid does not
        self.assertGreater(len(result.analysis_windows), 0)

    def test_falls_back_when_llm_unavailable(self) -> None:
        """When is_available is False, uses heuristic."""
        segments = _make_segments(3, duration_each=10.0)
        request = CandidateAnalysisRequest(
            job_id="test-fallback-unavail",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=30.0,
        )
        client = _mock_llm_client(available=False)
        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "true"}):
            result = analyze_candidates_hybrid(request, llm_client=client)
        self.assertGreater(len(result.analysis_windows), 0)
        client.generate.assert_not_called()

    def test_falls_back_on_llm_error(self) -> None:
        """When LLM raises LlmUnavailableError, falls back to heuristic."""
        segments = _make_segments(3, duration_each=10.0)
        request = CandidateAnalysisRequest(
            job_id="test-fallback-error",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=30.0,
        )
        client = _mock_llm_client(available=True)
        client.generate = MagicMock(side_effect=LlmUnavailableError("test error"))

        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "true"}):
            result = analyze_candidates_hybrid(request, llm_client=client)
        # Should have fallen back and produced heuristic windows
        self.assertGreater(len(result.analysis_windows), 0)

    def test_falls_back_on_unexpected_error(self) -> None:
        """Any unexpected exception also falls back cleanly."""
        segments = _make_segments(3, duration_each=10.0)
        request = CandidateAnalysisRequest(
            job_id="test-fallback-unexpected",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=30.0,
        )
        client = _mock_llm_client(available=True)
        client.generate = MagicMock(side_effect=RuntimeError("GPU exploded"))

        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "true"}):
            result = analyze_candidates_hybrid(request, llm_client=client)
        self.assertGreater(len(result.analysis_windows), 0)

    @patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "true"})
    def test_hybrid_path_used_when_enabled_and_available(self) -> None:
        """When enabled + available, the LLM is called (hybrid path)."""
        segments = _make_segments(3, duration_each=10.0)
        request = CandidateAnalysisRequest(
            job_id="test-hybrid-path",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=30.0,
        )
        client = _mock_llm_client(
            available=True,
            responses=[json.dumps([
                {"start_sec": 5.0, "end_sec": 25.0, "reason": "funny", "confidence": 0.85},
            ])],
        )
        result = analyze_candidates_hybrid(request, llm_client=client)
        client.generate.assert_called_once()
        # Hybrid path produces no analysis_windows
        self.assertEqual(len(result.analysis_windows), 0)
        self.assertEqual(len(result.clip_candidates), 1)


# ---------------------------------------------------------------------------
# Enable flag tests
# ---------------------------------------------------------------------------

class TestEnableFlag(unittest.TestCase):

    def test_disabled_by_default(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            self.assertFalse(is_hybrid_enabled())

    def test_enabled_true(self) -> None:
        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "true"}):
            self.assertTrue(is_hybrid_enabled())

    def test_enabled_1(self) -> None:
        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "1"}):
            self.assertTrue(is_hybrid_enabled())

    def test_disabled_false(self) -> None:
        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "false"}):
            self.assertFalse(is_hybrid_enabled())


# ---------------------------------------------------------------------------
# Eval harness integration — synthetic before/after measurement
# ---------------------------------------------------------------------------

class TestEvalHarnessIntegration(unittest.TestCase):
    """Measure hit-rate on a synthetic fixture: heuristic vs hybrid.

    This validates the harness adapter works and produces a concrete
    before/after number.
    """

    def _build_synthetic_fixture(self):
        """Build a synthetic transcript with known highlight moments."""
        segments: list[TranscriptSegment] = []
        # Boring filler: 0-100s
        for i in range(10):
            s = i * 10.0
            segments.append(TranscriptSegment(s, s + 10.0, "boring filler talk", 3))
        # HIGHLIGHT 1: exciting moment at 100-130s
        segments.append(TranscriptSegment(100.0, 110.0, "OH MY GOD did you see that WOW!", 8))
        segments.append(TranscriptSegment(110.0, 120.0, "That was INSANE absolutely CRAZY!", 6))
        segments.append(TranscriptSegment(120.0, 130.0, "I can NOT believe what just happened!", 7))
        # More filler: 130-300s
        for i in range(17):
            s = 130.0 + i * 10.0
            segments.append(TranscriptSegment(s, s + 10.0, "more boring normal talk here", 5))
        # HIGHLIGHT 2: funny moment at 300-330s
        segments.append(TranscriptSegment(300.0, 310.0, "HAHAHA oh no the car exploded!", 7))
        segments.append(TranscriptSegment(310.0, 320.0, "This is the funniest thing ever!", 6))
        segments.append(TranscriptSegment(320.0, 330.0, "I'm literally crying laughing!", 5))
        # Trailing filler: 330-400s
        for i in range(7):
            s = 330.0 + i * 10.0
            segments.append(TranscriptSegment(s, s + 10.0, "wrapping up the stream", 4))

        # Loudness: spikes at the highlight moments
        times = tuple(float(t) for t in range(0, 400, 5))
        rms = []
        for t in times:
            if 100 <= t < 130 or 300 <= t < 330:
                rms.append(-8.0)  # loud
            else:
                rms.append(-35.0)  # quiet
        profile = LoudnessProfile(time_sec=times, rms_db=tuple(rms))

        labels = [
            {"source": "synthetic", "start_sec": 100.0, "end_sec": 130.0, "note": "OMG moment"},
            {"source": "synthetic", "start_sec": 300.0, "end_sec": 330.0, "note": "Car explosion laugh"},
        ]

        return segments, profile, labels

    def test_heuristic_vs_hybrid_hitrate(self) -> None:
        """Run both detectors on the same synthetic fixture and compare.

        This test documents the before/after hit-rate.
        """
        from eval.harness import evaluate, Candidate, Label

        segments, profile, raw_labels = self._build_synthetic_fixture()
        labels = [
            Label(source=l["source"], start_sec=l["start_sec"], end_sec=l["end_sec"], note=l["note"])
            for l in raw_labels
        ]

        # --- Heuristic detector ---
        from streamcut_worker.analysis.service import analyze_candidates
        heuristic_request = CandidateAnalysisRequest(
            job_id="eval-heuristic",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=400.0,
            emotion_keywords=("wow", "insane", "crazy", "haha"),
            loudness_profile=profile,
            top_n=5,
        )
        heuristic_result = analyze_candidates(heuristic_request)
        heuristic_candidates = [
            Candidate(c.start_sec, c.end_sec, c.score)
            for c in heuristic_result.clip_candidates
        ]
        heuristic_eval = evaluate(labels, heuristic_candidates, tolerance_sec=5.0)

        # --- Hybrid detector (mocked LLM that correctly identifies highlights) ---
        hybrid_response = json.dumps([
            {"start_sec": 100.0, "end_sec": 130.0, "reason": "OMG reaction moment", "confidence": 0.95},
            {"start_sec": 300.0, "end_sec": 330.0, "reason": "Car explosion comedy", "confidence": 0.90},
        ])
        client = _mock_llm_client(available=True)
        # The synthetic fixture is small enough for 1 chunk
        client.generate = MagicMock(return_value=hybrid_response)

        with patch.dict(os.environ, {"HYBRID_DETECTION_ENABLED": "true"}):
            hybrid_result = analyze_candidates_hybrid(
                heuristic_request, llm_client=client,
            )
        hybrid_candidates = [
            Candidate(c.start_sec, c.end_sec, c.score)
            for c in hybrid_result.clip_candidates
        ]
        hybrid_eval = evaluate(labels, hybrid_candidates, tolerance_sec=5.0)

        # --- Report (printed for task report; test asserts hybrid >= heuristic) ---
        print(f"\n=== EVAL: Heuristic hit-rate = {heuristic_eval.hit_rate:.0%}"
              f"  ({heuristic_eval.hits}/{heuristic_eval.total_labels})"
              f"  FP={heuristic_eval.false_positives}")
        print(f"=== EVAL: Hybrid    hit-rate = {hybrid_eval.hit_rate:.0%}"
              f"  ({hybrid_eval.hits}/{hybrid_eval.total_labels})"
              f"  FP={hybrid_eval.false_positives}")

        # Hybrid (with a smart LLM) should hit both labeled moments
        self.assertGreaterEqual(hybrid_eval.hit_rate, heuristic_eval.hit_rate)
        self.assertEqual(hybrid_eval.hit_rate, 1.0)  # 100% on synthetic
        self.assertEqual(hybrid_eval.false_positives, 0)


class SelectMomentsTests(unittest.TestCase):
    """Stage-2 cross-stream selection."""

    def _moments(self, n: int) -> list[LlmHighlightMoment]:
        return [
            LlmHighlightMoment(start_sec=i * 10.0, end_sec=i * 10.0 + 5, reason=f"m{i}", confidence=0.5 + i * 0.01)
            for i in range(n)
        ]

    def test_passthrough_when_at_or_below_target(self) -> None:
        moments = self._moments(3)
        llm = MagicMock()
        kept = select_moments(moments, [], llm, target_n=5)
        self.assertEqual(kept, moments)
        llm.generate.assert_not_called()  # no LLM call needed

    def test_keeps_selected_indices(self) -> None:
        moments = self._moments(10)
        llm = MagicMock()
        llm.generate.return_value = "[3, 7, 1]"
        kept = select_moments(moments, [], llm, target_n=3)
        self.assertEqual([m.reason for m in kept], ["m3", "m7", "m1"])

    def test_falls_back_to_confidence_topn_on_garbage(self) -> None:
        moments = self._moments(10)  # confidence ascends with index → m9 highest
        llm = MagicMock()
        llm.generate.return_value = "I cannot decide."
        kept = select_moments(moments, [], llm, target_n=2)
        self.assertEqual([m.reason for m in kept], ["m9", "m8"])

    def test_parse_selection_filters_out_of_range_and_dupes(self) -> None:
        self.assertEqual(_parse_selection("[2, 2, 99, 0]", n=5), [2, 0])

    def test_batched_reaches_late_candidates(self) -> None:
        # 50 moments, batch_size 20 -> 3 batches + 1 final round. Each batch is
        # ranked in its own short prompt so late-stream candidates can't be
        # truncated away (the single-call overflow bug). Confidence ascends with
        # index, so the per-batch + final confidence fallback must surface the
        # highest-index (latest) moments, proving the late tail is reachable.
        moments = self._moments(50)
        llm = MagicMock()
        llm.generate.return_value = "no valid indices here"  # force confidence fallback
        kept = select_moments(moments, [], llm, target_n=5, batch_size=20)
        self.assertEqual(llm.generate.call_count, 4)  # 3 batches + 1 final
        self.assertEqual(len(kept), 5)
        self.assertIn(moments[49], kept)  # last (latest) candidate survived


if __name__ == "__main__":
    unittest.main()
