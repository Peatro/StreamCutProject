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
    WEIGHT_LOUDNESS,
    WEIGHT_EMOTION,
    WEIGHT_CONTINUITY,
    WEIGHT_SILENCE,
)
from streamcut_worker.silence import SilenceInterval
from streamcut_worker.transcription import TranscriptSegment


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


if __name__ == "__main__":
    unittest.main()
