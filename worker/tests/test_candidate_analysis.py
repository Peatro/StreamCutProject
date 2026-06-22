from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.analysis import (
    CandidateAnalysisRequest,
    LoudnessProfile,
    SlidingWindowCandidateAnalysisService,
)
from streamcut_worker.analysis.service import (
    HOOK_LEAD_IN_SEC,
    WEIGHT_LOUDNESS,
    WEIGHT_EMOTION,
    WEIGHT_CONTINUITY,
    WEIGHT_SILENCE,
    _shift_start_to_peak,
    _snap_to_boundary,
    _window_peak_loudness_time,
)
from streamcut_worker.silence import SilenceInterval
from streamcut_worker.transcription import TranscriptSegment
from streamcut_worker.transcription.models import TranscriptWord


class CandidateAnalysisServiceTests(unittest.TestCase):
    def test_analyze_computes_window_metrics_and_scores(self) -> None:
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0, top_n=2)
        request = CandidateAnalysisRequest(
            job_id="job-1",
            transcript_segments=[
                TranscriptSegment(0.0, 10.0, "amazing", 2),
                TranscriptSegment(10.0, 20.0, "amazing amazing", 4),
            ],
            silence_segments=[
                SilenceInterval(2.0, 4.0, 2.0),
                SilenceInterval(12.0, 14.0, 2.0),
            ],
            duration_sec=20.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            top_n=2,
            emotion_keywords=("amazing",),
        )

        result = service.analyze(request)

        self.assertEqual(result.job_id, "job-1")
        self.assertEqual(result.duration_sec, 20.0)
        self.assertEqual(len(result.analysis_windows), 2)
        self.assertEqual(result.analysis_windows[0].start_sec, 0.0)
        self.assertAlmostEqual(result.analysis_windows[0].speech_density, 0.2)
        self.assertAlmostEqual(result.analysis_windows[0].silence_ratio, 0.2)
        self.assertEqual(result.analysis_windows[0].emotion_hits, 1)
        self.assertAlmostEqual(result.analysis_windows[0].continuity_score, 1.0)
        self.assertAlmostEqual(result.analysis_windows[0].total_score, 0.675)
        self.assertAlmostEqual(result.analysis_windows[1].speech_density, 0.4)
        self.assertEqual(result.analysis_windows[1].emotion_hits, 2)
        self.assertAlmostEqual(result.analysis_windows[1].total_score, 0.95)

        payload = result.to_payload()
        self.assertEqual(payload["jobId"], "job-1")
        self.assertEqual(payload["analysisWindows"][0]["emotionHits"], 1)
        self.assertEqual(payload["clipCandidates"][0]["score"], 0.95)
        self.assertTrue(payload["clipCandidates"][0]["transcriptExcerpt"])

    def test_analyze_deduplicates_overlapping_candidates(self) -> None:
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=20.0, step_sec=5.0)
        transcript_segments = [
            TranscriptSegment(0.0, 10.0, "wow wow wow", 3),
            TranscriptSegment(10.0, 20.0, "wow wow wow", 3),
            TranscriptSegment(40.0, 50.0, "wow wow wow wow", 4),
            TranscriptSegment(50.0, 60.0, "wow wow wow wow", 4),
        ]
        request = CandidateAnalysisRequest(
            job_id="job-2",
            transcript_segments=transcript_segments,
            silence_segments=[],
            duration_sec=60.0,
            window_duration_sec=20.0,
            step_sec=5.0,
            emotion_keywords=("wow",),
        )

        result = service.analyze(request)

        self.assertGreaterEqual(len(result.clip_candidates), 3)
        self.assertEqual(result.clip_candidates[0].start_sec, 40.0)
        self.assertEqual(result.clip_candidates[1].start_sec, 0.0)
        self.assertGreaterEqual(result.clip_candidates[0].score, result.clip_candidates[1].score)
        for candidate in result.clip_candidates:
            self.assertLessEqual(candidate.end_sec - candidate.start_sec, 20.0)

        for left in result.clip_candidates:
            for right in result.clip_candidates:
                if left is right:
                    continue
                overlap = max(0.0, min(left.end_sec, right.end_sec) - max(left.start_sec, right.start_sec))
                union = max(left.end_sec, right.end_sec) - min(left.start_sec, right.start_sec)
                if union > 0:
                    self.assertEqual(overlap / union, 0.0)

    def test_analyze_returns_all_non_overlapping_candidates_when_limit_is_unset(self) -> None:
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0)
        request = CandidateAnalysisRequest(
            job_id="job-3",
            transcript_segments=[
                TranscriptSegment(0.0, 10.0, "clip one", 2),
                TranscriptSegment(10.0, 20.0, "clip two", 2),
                TranscriptSegment(20.0, 30.0, "clip three", 2),
                TranscriptSegment(30.0, 40.0, "clip four", 2),
            ],
            silence_segments=[],
            duration_sec=40.0,
            window_duration_sec=10.0,
            step_sec=10.0,
        )

        result = service.analyze(request)

        self.assertEqual([candidate.start_sec for candidate in result.clip_candidates], [0.0, 10.0, 20.0, 30.0])
        self.assertEqual(len(result.clip_candidates), 4)

    def test_analyze_handles_empty_inputs(self) -> None:
        service = SlidingWindowCandidateAnalysisService()
        request = CandidateAnalysisRequest(
            job_id="job-4",
            transcript_segments=[],
            silence_segments=[],
            duration_sec=0.0,
        )

        result = service.analyze(request)

        self.assertEqual(result.analysis_windows, [])
        self.assertEqual(result.clip_candidates, [])

    def test_loudness_raises_window_score(self) -> None:
        """A window with high loudness should score higher than the same window without."""
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0)

        # Two identical windows at 0-10s and 10-20s, same transcript.
        segments = [
            TranscriptSegment(0.0, 10.0, "hello world", 2),
            TranscriptSegment(10.0, 20.0, "hello world", 2),
        ]

        # Loudness profile: window 0-10 is quiet (-40 dB), window 10-20 is loud (-10 dB).
        loud_profile = LoudnessProfile(
            time_sec=(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0,
                       10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0),
            rms_db=(-40.0,) * 10 + (-10.0,) * 10,
        )

        request = CandidateAnalysisRequest(
            job_id="job-loud-1",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=20.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=loud_profile,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.analysis_windows), 2)
        quiet_window = result.analysis_windows[0]  # 0-10s, quiet
        loud_window = result.analysis_windows[1]    # 10-20s, loud

        # The loud window should have a higher normalized loudness
        self.assertGreater(loud_window.loudness, quiet_window.loudness)
        # The loud window should score higher overall
        self.assertGreater(loud_window.total_score, quiet_window.total_score)

    def test_loud_window_beats_high_density_quiet_window(self) -> None:
        """With re-weighted scoring, a loud window outranks a higher talk-density quiet window."""
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0, top_n=2)

        # Window 0-10s: dense monologue (high speech density), quiet audio.
        # Window 10-20s: sparse speech, but very loud audio (hype moment).
        segments = [
            TranscriptSegment(0.0, 10.0, "this is a very long dense sponsor read with lots of words", 11),
            TranscriptSegment(10.0, 12.0, "WOW!", 1),
        ]

        # Loudness: window 0-10 is quiet (-45 dB), window 10-20 is very loud (-5 dB)
        loud_profile = LoudnessProfile(
            time_sec=(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0,
                       10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0),
            rms_db=(-45.0,) * 10 + (-5.0,) * 10,
        )

        request = CandidateAnalysisRequest(
            job_id="job-loud-2",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=20.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=loud_profile,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.clip_candidates), 2)
        # The loud/sparse window should be the top candidate
        self.assertEqual(result.clip_candidates[0].start_sec, 10.0)

    def test_shifted_candidate_score_reflects_clip_window_not_original(self) -> None:
        """After the hook shift trims the quiet front, the candidate score is
        recomputed for the actual clip window — so it should differ from (and,
        when the trimmed front was silent, exceed) the original window score."""
        service = SlidingWindowCandidateAnalysisService(
            window_duration_sec=20.0, step_sec=20.0, top_n=1
        )
        # Front 0-16s is silent; the only speech + the loudness peak sit at the end.
        segments = [TranscriptSegment(16.0, 20.0, "insane clutch win", 3)]
        loud_profile = LoudnessProfile(
            time_sec=tuple(float(t) for t in range(21)),
            rms_db=tuple(-40.0 if t != 18 else -5.0 for t in range(21)),
        )
        request = CandidateAnalysisRequest(
            job_id="job-rescore",
            transcript_segments=segments,
            silence_segments=[SilenceInterval(0.0, 16.0, 16.0)],
            duration_sec=20.0,
            window_duration_sec=20.0,
            step_sec=20.0,
            top_n=1,
            loudness_profile=loud_profile,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.clip_candidates), 1)
        candidate = result.clip_candidates[0]
        original_window_score = result.analysis_windows[0].total_score
        # Start shifted out of the silent front toward the peak.
        self.assertGreater(candidate.start_sec, 0.0)
        # Score now describes [shifted_start, 20] (almost no silence) and beats
        # the original [0, 20] window score (80% silent).
        self.assertGreater(candidate.score, original_window_score)
        # Excerpt is built from the clip window, so the end-speech survives.
        self.assertIn("clutch", candidate.transcript_excerpt)

    def test_wordless_window_has_no_phantom_speech(self) -> None:
        """A segment whose time range spans a window but whose WORDS sit outside
        it must not credit that window with speech/continuity/emotion (the job-13
        bug: continuity=1.0 + empty excerpt scoring ~0.8)."""
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0)
        # One segment spans 0-20s, but every word is in the first 8 seconds.
        words = [
            TranscriptWord("hello", 0.5, 1.0),
            TranscriptWord("there", 1.0, 1.5),
            TranscriptWord("friends", 6.0, 8.0),
        ]
        segments = [TranscriptSegment(0.0, 20.0, "hello there friends", 3, words)]
        # The wordless second half (10-20s) is the loudest part of the VOD.
        loud_profile = LoudnessProfile(
            time_sec=tuple(float(t) for t in range(20)),
            rms_db=tuple(-40.0 if t < 10 else -5.0 for t in range(20)),
        )
        request = CandidateAnalysisRequest(
            job_id="job-wordless",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=20.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=loud_profile,
        )

        result = service.analyze(request)
        by_start = {round(w.start_sec): w for w in result.analysis_windows}

        # Loud but wordless window: no phantom continuous speech.
        self.assertEqual(by_start[10].continuity_score, 0.0)
        self.assertEqual(by_start[10].speech_density, 0.0)
        self.assertEqual(by_start[10].emotion_hits, 0)
        # The candidate covering that window carries an empty excerpt and no
        # longer outscores everything on phantom speech (was ~0.8).
        wordless = next(c for c in result.clip_candidates if 10 <= c.start_sec < 20)
        self.assertEqual(wordless.transcript_excerpt, "")
        self.assertLess(wordless.score, 0.65)

    def test_missing_loudness_profile_still_produces_candidates(self) -> None:
        """When loudness_profile is None, analysis falls back to the prior formula."""
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0)

        segments = [
            TranscriptSegment(0.0, 10.0, "hello world", 2),
            TranscriptSegment(10.0, 20.0, "goodbye world", 2),
        ]

        request = CandidateAnalysisRequest(
            job_id="job-no-loud",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=20.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=None,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.analysis_windows), 2)
        self.assertGreater(len(result.clip_candidates), 0)
        # All windows should have loudness = 0.0 (fallback)
        for window in result.analysis_windows:
            self.assertEqual(window.loudness, 0.0)

    def test_empty_loudness_profile_still_produces_candidates(self) -> None:
        """When loudness_profile has empty sample arrays, analysis falls back gracefully."""
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0)

        segments = [
            TranscriptSegment(0.0, 10.0, "hello world", 2),
        ]

        empty_profile = LoudnessProfile(time_sec=(), rms_db=())

        request = CandidateAnalysisRequest(
            job_id="job-empty-loud",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=10.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=empty_profile,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.analysis_windows), 1)
        self.assertGreater(len(result.clip_candidates), 0)
        self.assertEqual(result.analysis_windows[0].loudness, 0.0)

    def test_scoring_weights_are_correctly_applied(self) -> None:
        """Verify the weight constants match the spec."""
        self.assertAlmostEqual(WEIGHT_LOUDNESS, 0.45)
        self.assertAlmostEqual(WEIGHT_EMOTION, 0.20)
        self.assertAlmostEqual(WEIGHT_CONTINUITY, 0.20)
        self.assertAlmostEqual(WEIGHT_SILENCE, 0.15)
        # Sum should be 1.0
        self.assertAlmostEqual(
            WEIGHT_LOUDNESS + WEIGHT_EMOTION + WEIGHT_CONTINUITY + WEIGHT_SILENCE,
            1.0,
        )

    def test_transport_payload_does_not_include_loudness(self) -> None:
        """The transport payload shape must remain unchanged (no loudness field)."""
        service = SlidingWindowCandidateAnalysisService(window_duration_sec=10.0, step_sec=10.0)

        loud_profile = LoudnessProfile(
            time_sec=(0.0, 5.0),
            rms_db=(-20.0, -10.0),
        )

        request = CandidateAnalysisRequest(
            job_id="job-payload",
            transcript_segments=[TranscriptSegment(0.0, 10.0, "test", 1)],
            silence_segments=[],
            duration_sec=10.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=loud_profile,
        )

        result = service.analyze(request)
        payload = result.to_payload()

        # The analysisWindows entries should NOT have a loudness key
        for window_payload in payload["analysisWindows"]:
            self.assertNotIn("loudness", window_payload)
            # But should still have the expected keys
            self.assertIn("totalScore", window_payload)
            self.assertIn("speechDensity", window_payload)


class HookStartShiftTests(unittest.TestCase):
    """Tests for TASK-090: shift clip start to the action peak."""

    def test_hook_lead_in_default(self) -> None:
        """HOOK_LEAD_IN_SEC should default to ~0.4s."""
        self.assertAlmostEqual(HOOK_LEAD_IN_SEC, 0.4)

    def test_service_hook_lead_in_default(self) -> None:
        """The service dataclass should default to the module constant."""
        service = SlidingWindowCandidateAnalysisService()
        self.assertAlmostEqual(service.hook_lead_in_sec, HOOK_LEAD_IN_SEC)

    # --- _window_peak_loudness_time ---

    def test_peak_loudness_time_returns_time_of_max(self) -> None:
        profile = LoudnessProfile(
            time_sec=(0.0, 1.0, 2.0, 3.0, 4.0),
            rms_db=(-30.0, -20.0, -5.0, -15.0, -25.0),
        )
        self.assertEqual(_window_peak_loudness_time(profile, 0.0, 5.0), 2.0)

    def test_peak_loudness_time_none_without_profile(self) -> None:
        self.assertIsNone(_window_peak_loudness_time(None, 0.0, 10.0))

    def test_peak_loudness_time_none_empty_profile(self) -> None:
        self.assertIsNone(_window_peak_loudness_time(LoudnessProfile((), ()), 0.0, 10.0))

    def test_peak_loudness_time_scoped_to_window(self) -> None:
        profile = LoudnessProfile(
            time_sec=(0.0, 5.0, 10.0, 15.0),
            rms_db=(-5.0, -30.0, -10.0, -40.0),
        )
        # Window 10-20: only samples at t=10 and t=15 are in range.
        self.assertEqual(_window_peak_loudness_time(profile, 10.0, 20.0), 10.0)

    # --- _snap_to_boundary ---

    def test_snap_to_word_boundary(self) -> None:
        segments = [
            TranscriptSegment(
                0.0, 5.0, "hello world", 2,
                words=[
                    TranscriptWord("hello", 0.0, 0.5),
                    TranscriptWord("world", 0.6, 1.1),
                ],
            ),
        ]
        # Target 0.55 should snap to 0.5 (end of "hello") or 0.6 (start of "world").
        result = _snap_to_boundary(0.55, segments, [])
        self.assertIn(result, (0.5, 0.6))

    def test_snap_to_silence_boundary(self) -> None:
        silence = [SilenceInterval(4.8, 5.3, 0.5)]
        result = _snap_to_boundary(5.0, [], silence)
        # Closest edge: 4.8 (dist 0.2) or 5.3 (dist 0.3) -> 4.8
        self.assertAlmostEqual(result, 4.8)

    def test_snap_returns_target_when_no_boundary_nearby(self) -> None:
        """When no boundary is within the search radius, return the target unchanged."""
        result = _snap_to_boundary(50.0, [], [])
        self.assertAlmostEqual(result, 50.0)

    def test_snap_uses_segment_boundaries_when_no_words(self) -> None:
        segments = [TranscriptSegment(2.0, 4.0, "hi", 1)]
        result = _snap_to_boundary(2.1, segments, [])
        self.assertAlmostEqual(result, 2.0)

    # --- _shift_start_to_peak ---

    def test_shift_moves_start_to_peak_minus_lead_in(self) -> None:
        """Core behavior: start moves to peak - lead_in."""
        profile = LoudnessProfile(
            time_sec=(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0),
            rms_db=(-30.0, -25.0, -20.0, -15.0, -5.0, -10.0, -20.0, -25.0, -30.0, -35.0),
        )
        # Peak is at t=14.0. lead_in=0.4 -> raw_start=13.6
        # No transcript/silence boundaries nearby, so snapping is a no-op.
        result = _shift_start_to_peak(
            window_start=10.0,
            window_end=20.0,
            lead_in_sec=0.4,
            loudness_profile=profile,
            transcript_segments=[],
            silence_segments=[],
        )
        self.assertAlmostEqual(result, 13.6)

    def test_shift_snaps_to_word_boundary(self) -> None:
        """After computing peak - lead_in, the start should snap to a word boundary."""
        profile = LoudnessProfile(
            time_sec=(10.0, 11.0, 12.0, 13.0, 14.0),
            rms_db=(-30.0, -25.0, -20.0, -5.0, -15.0),
        )
        # Peak at t=13.0. lead_in=0.4 -> raw_start=12.6
        segments = [
            TranscriptSegment(
                10.0, 15.0, "one two three four", 4,
                words=[
                    TranscriptWord("one", 10.0, 10.5),
                    TranscriptWord("two", 11.0, 11.5),
                    TranscriptWord("three", 12.0, 12.5),
                    TranscriptWord("four", 12.7, 13.2),
                ],
            ),
        ]
        result = _shift_start_to_peak(
            window_start=10.0,
            window_end=15.0,
            lead_in_sec=0.4,
            loudness_profile=profile,
            transcript_segments=segments,
            silence_segments=[],
        )
        # raw_start=12.6, nearest word boundaries: 12.5 (end of "three") and 12.7 (start of "four")
        # 12.5 is dist 0.1, 12.7 is dist 0.1 -> either is valid; both within [10.0, 13.0]
        self.assertIn(result, (12.5, 12.7))

    def test_shift_clamps_to_window_start(self) -> None:
        """When peak is near the window start, the shifted start should not go before it."""
        profile = LoudnessProfile(
            time_sec=(10.0, 10.1, 10.2),
            rms_db=(-5.0, -10.0, -15.0),
        )
        # Peak at t=10.0. lead_in=0.4 -> raw_start=9.6, clamped to 10.0
        result = _shift_start_to_peak(
            window_start=10.0,
            window_end=20.0,
            lead_in_sec=0.4,
            loudness_profile=profile,
            transcript_segments=[],
            silence_segments=[],
        )
        self.assertAlmostEqual(result, 10.0)

    def test_shift_clamps_at_zero(self) -> None:
        """When the window starts at 0 and peak is early, start should not go negative."""
        profile = LoudnessProfile(
            time_sec=(0.0, 0.1, 0.2, 0.3),
            rms_db=(-15.0, -5.0, -10.0, -20.0),
        )
        # Peak at t=0.1. lead_in=0.4 -> raw_start=-0.3, clamped to 0.0
        result = _shift_start_to_peak(
            window_start=0.0,
            window_end=10.0,
            lead_in_sec=0.4,
            loudness_profile=profile,
            transcript_segments=[],
            silence_segments=[],
        )
        self.assertAlmostEqual(result, 0.0)

    def test_shift_fallback_when_no_loudness(self) -> None:
        """Without loudness data, the start should not move."""
        result = _shift_start_to_peak(
            window_start=5.0,
            window_end=15.0,
            lead_in_sec=0.4,
            loudness_profile=None,
            transcript_segments=[],
            silence_segments=[],
        )
        self.assertAlmostEqual(result, 5.0)

    def test_shift_does_not_exceed_peak_time(self) -> None:
        """The shifted start should never go past the peak time."""
        profile = LoudnessProfile(
            time_sec=(10.0, 11.0, 12.0),
            rms_db=(-30.0, -5.0, -20.0),
        )
        # Peak at t=11.0. lead_in=0.4 -> raw_start=10.6
        # Silence boundary at 10.95 (closer, still < peak)
        silence = [SilenceInterval(10.9, 10.95, 0.05)]
        result = _shift_start_to_peak(
            window_start=10.0,
            window_end=13.0,
            lead_in_sec=0.4,
            loudness_profile=profile,
            transcript_segments=[],
            silence_segments=silence,
        )
        self.assertLessEqual(result, 11.0)

    def test_shift_respects_min_clip_floor(self) -> None:
        """Peak near the window end must not trim the clip below min_clip_sec."""
        profile = LoudnessProfile(
            time_sec=(10.0, 26.0, 27.0, 28.0, 29.0),
            rms_db=(-30.0, -25.0, -20.0, -5.0, -25.0),
        )
        # Peak at t=28.0; lead_in 0.4 -> raw 27.6 would leave a 2.4s clip.
        # min_clip_sec=8 caps start at window_end - 8 = 22.0.
        result = _shift_start_to_peak(
            window_start=10.0,
            window_end=30.0,
            lead_in_sec=0.4,
            min_clip_sec=8.0,
            loudness_profile=profile,
            transcript_segments=[],
            silence_segments=[],
        )
        self.assertAlmostEqual(result, 22.0)

    def test_shift_min_clip_floor_never_precedes_window_start(self) -> None:
        """If the window itself is shorter than min_clip_sec, stay at window_start."""
        profile = LoudnessProfile(
            time_sec=(10.0, 14.0, 15.0),
            rms_db=(-30.0, -5.0, -25.0),
        )
        # Window is only 5s; min_clip_sec=8 can't be met -> clamp to window_start.
        result = _shift_start_to_peak(
            window_start=10.0,
            window_end=15.0,
            lead_in_sec=0.4,
            min_clip_sec=8.0,
            loudness_profile=profile,
            transcript_segments=[],
            silence_segments=[],
        )
        self.assertAlmostEqual(result, 10.0)

    # --- Integration: full analyze pipeline with hook shift ---

    def test_analyze_shifts_candidate_start_with_loudness(self) -> None:
        """Full-pipeline test: candidates should have their start shifted toward the peak."""
        # hook_min_clip_sec=0 isolates shift behavior; the floor has its own test.
        service = SlidingWindowCandidateAnalysisService(
            window_duration_sec=10.0, step_sec=10.0, top_n=1, hook_min_clip_sec=0.0,
        )

        segments = [
            TranscriptSegment(0.0, 10.0, "hello world", 2),
        ]

        # Peak loudness is at t=7.0 (-5 dB).
        profile = LoudnessProfile(
            time_sec=(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0),
            rms_db=(-30.0, -28.0, -25.0, -20.0, -15.0, -12.0, -8.0, -5.0, -10.0, -20.0),
        )

        request = CandidateAnalysisRequest(
            job_id="job-hook-1",
            transcript_segments=segments,
            silence_segments=[],
            duration_sec=10.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=profile,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.clip_candidates), 1)
        candidate = result.clip_candidates[0]
        # Peak at 7.0, lead_in 0.4 -> raw 6.6, no nearby boundaries -> 6.6
        self.assertAlmostEqual(candidate.start_sec, 6.6)
        # End should be unchanged.
        self.assertAlmostEqual(candidate.end_sec, 10.0)

    def test_analyze_preserves_start_without_loudness(self) -> None:
        """Without loudness, candidate start should remain at the window edge."""
        service = SlidingWindowCandidateAnalysisService(
            window_duration_sec=10.0, step_sec=10.0, top_n=1,
        )

        request = CandidateAnalysisRequest(
            job_id="job-hook-2",
            transcript_segments=[TranscriptSegment(0.0, 10.0, "hello", 1)],
            silence_segments=[],
            duration_sec=10.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=None,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.clip_candidates), 1)
        self.assertAlmostEqual(result.clip_candidates[0].start_sec, 0.0)

    def test_analyze_custom_lead_in(self) -> None:
        """The hook_lead_in_sec field on the service should be respected."""
        service = SlidingWindowCandidateAnalysisService(
            window_duration_sec=10.0, step_sec=10.0, top_n=1,
            hook_lead_in_sec=1.0, hook_min_clip_sec=0.0,
        )

        profile = LoudnessProfile(
            time_sec=(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0),
            rms_db=(-30.0, -28.0, -25.0, -20.0, -15.0, -12.0, -8.0, -5.0, -10.0, -20.0),
        )

        request = CandidateAnalysisRequest(
            job_id="job-hook-3",
            transcript_segments=[TranscriptSegment(0.0, 10.0, "hello", 1)],
            silence_segments=[],
            duration_sec=10.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=profile,
        )

        result = service.analyze(request)

        self.assertEqual(len(result.clip_candidates), 1)
        # Peak at 7.0, lead_in=1.0 -> raw 6.0. No nearby boundaries -> 6.0
        self.assertAlmostEqual(result.clip_candidates[0].start_sec, 6.0)

    def test_analyze_score_and_end_preserved(self) -> None:
        """Hook shift must not alter the score or end of the candidate."""
        service = SlidingWindowCandidateAnalysisService(
            window_duration_sec=10.0, step_sec=10.0, top_n=1,
        )

        profile = LoudnessProfile(
            time_sec=(0.0, 5.0, 9.0),
            rms_db=(-20.0, -5.0, -30.0),
        )

        request = CandidateAnalysisRequest(
            job_id="job-hook-4",
            transcript_segments=[TranscriptSegment(0.0, 10.0, "test", 1)],
            silence_segments=[],
            duration_sec=10.0,
            window_duration_sec=10.0,
            step_sec=10.0,
            loudness_profile=profile,
        )

        result = service.analyze(request)

        candidate = result.clip_candidates[0]
        # End must be the original window end.
        self.assertAlmostEqual(candidate.end_sec, 10.0)
        # Score must match the analysis window score (not recomputed).
        self.assertAlmostEqual(candidate.score, result.analysis_windows[0].total_score)

    def test_snap_prefers_closest_boundary(self) -> None:
        """When multiple boundaries are nearby, snap picks the closest one."""
        segments = [
            TranscriptSegment(
                0.0, 10.0, "a b c", 3,
                words=[
                    TranscriptWord("a", 4.0, 4.3),
                    TranscriptWord("b", 4.5, 4.8),
                    TranscriptWord("c", 5.2, 5.5),
                ],
            ),
        ]
        silence = [SilenceInterval(4.8, 5.2, 0.4)]

        # Target at 5.0: boundaries are 4.8 (sil start, dist 0.2), 5.2 (sil end / word start, dist 0.2)
        # Both at same distance; either is acceptable.
        result = _snap_to_boundary(5.0, segments, silence)
        self.assertIn(result, (4.8, 5.2))


if __name__ == "__main__":
    unittest.main()
