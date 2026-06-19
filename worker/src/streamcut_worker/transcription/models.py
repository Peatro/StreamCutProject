from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass(frozen=True, slots=True)
class TranscriptWord:
    word: str
    start_sec: float
    end_sec: float


@dataclass(frozen=True, slots=True)
class TranscriptSegment:
    start_sec: float
    end_sec: float
    text: str
    word_count: int
    words: list[TranscriptWord] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class TranscriptionRequest:
    job_id: str
    audio_path: Path
    model_size: str = "large-v3-turbo"
    language: str | None = None
    beam_size: int = 5
    vad_filter: bool = True
    device: str = "cpu"
    compute_type: str = "int8"


@dataclass(frozen=True, slots=True)
class TranscriptionResult:
    job_id: str
    audio_path: Path
    duration_sec: float
    language: str | None
    language_probability: float | None
    transcript_segments: list[TranscriptSegment]

    def to_payload(self) -> dict[str, Any]:
        return {
            "jobId": self.job_id,
            "durationSec": self.duration_sec,
            "language": self.language,
            "transcriptSegments": [
                {
                    "startSec": segment.start_sec,
                    "endSec": segment.end_sec,
                    "text": segment.text,
                    "wordCount": segment.word_count,
                    "words": [
                        {
                            "word": w.word,
                            "startSec": w.start_sec,
                            "endSec": w.end_sec,
                        }
                        for w in segment.words
                    ] if segment.words else [],
                }
                for segment in self.transcript_segments
            ],
        }

