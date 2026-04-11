from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.analysis import (
    CandidateAnalysisRequest,
    SlidingWindowCandidateAnalysisService,
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


if __name__ == "__main__":
    unittest.main()
