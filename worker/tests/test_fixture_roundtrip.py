"""Round-trip test for the frozen-detector-input fixture (TASK-087 plumbing).

A frozen request must thaw back to an equivalent detector input and still run
through the detector -- otherwise the harness scores a corrupted input.
"""

from pathlib import Path
import sys
import unittest

_WORKER_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_WORKER_ROOT))
sys.path.insert(0, str(_WORKER_ROOT / "src"))

from streamcut_worker.analysis.fixture import dump_request, load_request, request_to_dict
from streamcut_worker.analysis.models import CandidateAnalysisRequest, LoudnessProfile
from streamcut_worker.analysis.service import analyze_candidates
from streamcut_worker.silence.models import SilenceInterval
from streamcut_worker.transcription.models import TranscriptSegment, TranscriptWord


def _sample_request() -> CandidateAnalysisRequest:
    return CandidateAnalysisRequest(
        job_id="42",
        transcript_segments=[
            TranscriptSegment(
                start_sec=0.0,
                end_sec=3.0,
                text="hello world this is a test",
                word_count=6,
                words=[
                    TranscriptWord(word="hello", start_sec=0.0, end_sec=0.5),
                    TranscriptWord(word="world", start_sec=0.5, end_sec=1.0),
                ],
            ),
            TranscriptSegment(start_sec=3.0, end_sec=6.0, text="more words here", word_count=3),
        ],
        silence_segments=[SilenceInterval(start_sec=6.0, end_sec=7.0, duration_sec=1.0)],
        duration_sec=30.0,
        emotion_keywords=("wow", "insane"),
        loudness_profile=LoudnessProfile(time_sec=(0.0, 1.0, 2.0), rms_db=(-20.0, -15.0, -25.0)),
    )


class FixtureRoundTripTests(unittest.TestCase):
    def test_dict_roundtrip_preserves_fields(self) -> None:
        request = _sample_request()
        thawed = load_request_from_dict_via_disk(request)

        self.assertEqual(thawed.job_id, request.job_id)
        self.assertEqual(thawed.duration_sec, request.duration_sec)
        self.assertEqual(thawed.emotion_keywords, request.emotion_keywords)
        self.assertEqual(len(thawed.transcript_segments), 2)
        self.assertEqual(thawed.transcript_segments[0].words[0].word, "hello")
        self.assertEqual(thawed.silence_segments[0].duration_sec, 1.0)
        self.assertIsNotNone(thawed.loudness_profile)
        self.assertEqual(thawed.loudness_profile.rms_db, (-20.0, -15.0, -25.0))

        # The thawed request must serialize identically (stable fixture format).
        self.assertEqual(request_to_dict(thawed), request_to_dict(request))

    def test_thawed_request_runs_through_detector(self) -> None:
        request = _sample_request()
        thawed = load_request_from_dict_via_disk(request)
        # Must not raise; detector output is deterministic for a fixed input.
        result_a = analyze_candidates(thawed)
        result_b = analyze_candidates(load_request_from_dict_via_disk(request))
        self.assertEqual(
            [(c.start_sec, c.end_sec, c.score) for c in result_a.clip_candidates],
            [(c.start_sec, c.end_sec, c.score) for c in result_b.clip_candidates],
        )

    def test_none_loudness_roundtrips(self) -> None:
        request = CandidateAnalysisRequest(job_id="1", transcript_segments=[], silence_segments=[])
        thawed = load_request_from_dict_via_disk(request)
        self.assertIsNone(thawed.loudness_profile)


def load_request_from_dict_via_disk(request: CandidateAnalysisRequest) -> CandidateAnalysisRequest:
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "req.json"
        dump_request(request, path)
        return load_request(path)


if __name__ == "__main__":
    unittest.main()
