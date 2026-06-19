from __future__ import annotations

from pathlib import Path
from tempfile import NamedTemporaryFile, TemporaryDirectory
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from streamcut_worker.transcription import (
    FasterWhisperTranscriptionService,
    TranscriptSegment,
    TranscriptWord,
    TranscriptionException,
    TranscriptionRequest,
    TranscriptionResult,
)


class FakeWord:
    def __init__(self, word: str, start: float = 0.0, end: float = 0.0) -> None:
        self.word = word
        self.start = start
        self.end = end


class FakeSegment:
    def __init__(self, start: float, end: float, text: str, words: list[FakeWord] | None = None) -> None:
        self.start = start
        self.end = end
        self.text = text
        self.words = words


class FakeInfo:
    def __init__(self, language: str | None, language_probability: float | None, duration: float | None) -> None:
        self.language = language
        self.language_probability = language_probability
        self.duration = duration


class FakeWhisperModel:
    def __init__(self, result: tuple[list[FakeSegment], FakeInfo]) -> None:
        self.result = result
        self.calls: list[tuple[str, dict[str, object]]] = []

    def transcribe(self, audio: str, **kwargs):
        self.calls.append((audio, kwargs))
        return self.result


class TranscriptionServiceTests(unittest.TestCase):
    def test_transcribe_maps_segments_language_and_duration(self) -> None:
        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
            tmp.write(b"dummy")
            audio_path = Path(tmp.name)

            model = FakeWhisperModel(
                (
                    [
                        FakeSegment(0.0, 1.2, "Hello world", [
                            FakeWord("Hello", 0.0, 0.5),
                            FakeWord("world", 0.6, 1.1),
                        ]),
                        FakeSegment(1.2, 2.8, "More text", None),
                    ],
                    FakeInfo("en", 0.97, 2.8),
                )
            )
            service = FasterWhisperTranscriptionService(model=model, model_name="test-model")
            progress_updates: list[tuple[float, float]] = []

            result = service.transcribe(
                TranscriptionRequest(
                    job_id="job-1",
                    audio_path=audio_path,
                    model_size="small",
                ),
                on_progress=lambda processed, total: progress_updates.append((processed, total)),
            )

        self.assertIsInstance(result, TranscriptionResult)
        self.assertEqual(result.job_id, "job-1")
        self.assertEqual(result.language, "en")
        self.assertEqual(result.duration_sec, 2.8)
        self.assertEqual(result.transcript_segments[0].start_sec, 0.0)
        self.assertEqual(result.transcript_segments[0].end_sec, 1.2)
        self.assertEqual(result.transcript_segments[0].text, "Hello world")
        self.assertEqual(result.transcript_segments[0].word_count, 2)
        self.assertEqual(result.transcript_segments[0].words, [
            TranscriptWord("Hello", 0.0, 0.5),
            TranscriptWord("world", 0.6, 1.1),
        ])
        self.assertEqual(result.transcript_segments[1].word_count, 2)
        self.assertEqual(result.transcript_segments[1].words, [])
        self.assertEqual(model.calls[0][0], str(audio_path))
        self.assertTrue(model.calls[0][1]["word_timestamps"])

        payload = result.to_payload()
        self.assertEqual(payload["jobId"], "job-1")
        self.assertEqual(payload["durationSec"], 2.8)
        self.assertEqual(payload["language"], "en")
        self.assertEqual(payload["transcriptSegments"][0]["wordCount"], 2)
        self.assertEqual(payload["transcriptSegments"][0]["words"], [
            {"word": "Hello", "startSec": 0.0, "endSec": 0.5},
            {"word": "world", "startSec": 0.6, "endSec": 1.1},
        ])
        self.assertEqual(payload["transcriptSegments"][1]["words"], [])
        self.assertEqual(progress_updates, [(1.2, 2.8), (2.8, 2.8), (2.8, 2.8)])

    def test_missing_audio_fails_fast(self) -> None:
        model = FakeWhisperModel(([], FakeInfo("en", 0.5, 0.0)))
        service = FasterWhisperTranscriptionService(model=model, model_name="test-model")

        with self.assertRaises(TranscriptionException) as ctx:
            service.transcribe(
                TranscriptionRequest(
                    job_id="job-1",
                    audio_path=Path("missing.wav"),
                )
            )

        self.assertIn("does not exist", str(ctx.exception))

    def test_model_errors_are_wrapped(self) -> None:
        class FailingModel(FakeWhisperModel):
            def transcribe(self, audio: str, **kwargs):
                raise RuntimeError("boom")

        with TemporaryDirectory() as _temp_dir, NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
            tmp.write(b"dummy")
            audio_path = Path(tmp.name)

            service = FasterWhisperTranscriptionService(
                model=FailingModel(([], FakeInfo(None, None, None))),
                model_name="test-model",
            )

            with self.assertRaises(TranscriptionException) as ctx:
                service.transcribe(
                    TranscriptionRequest(
                        job_id="job-1",
                        audio_path=audio_path,
                    )
                )

        self.assertIn("faster-whisper failed", str(ctx.exception))
        self.assertEqual(ctx.exception.stderr, "boom")


if __name__ == "__main__":
    unittest.main()
