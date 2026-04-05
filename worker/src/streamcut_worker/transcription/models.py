from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True, slots=True)
class TranscriptSegment:
    start_sec: float
    end_sec: float
    text: str
    word_count: int


@dataclass(frozen=True, slots=True)
class TranscriptionRequest:
    job_id: str
    audio_path: Path
    model_size: str = "small"
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
                }
                for segment in self.transcript_segments
            ],
        }

