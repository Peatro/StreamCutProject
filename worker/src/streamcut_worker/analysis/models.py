from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from streamcut_worker.silence.models import SilenceInterval
from streamcut_worker.transcription.models import TranscriptSegment


@dataclass(frozen=True, slots=True)
class AnalysisWindow:
    start_sec: float
    end_sec: float
    speech_density: float
    silence_ratio: float
    emotion_hits: int
    continuity_score: float
    total_score: float


@dataclass(frozen=True, slots=True)
class ClipCandidate:
    start_sec: float
    end_sec: float
    score: float
    transcript_excerpt: str


@dataclass(frozen=True, slots=True)
class CandidateAnalysisRequest:
    job_id: str
    transcript_segments: list[TranscriptSegment]
    silence_segments: list[SilenceInterval]
    duration_sec: float | None = None
    window_duration_sec: float = 30.0
    step_sec: float = 5.0
    top_n: int = 10
    min_overlap_ratio: float = 0.5
    emotion_keywords: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True, slots=True)
class CandidateAnalysisResult:
    job_id: str
    duration_sec: float
    analysis_windows: list[AnalysisWindow]
    clip_candidates: list[ClipCandidate]

    def to_payload(self) -> dict[str, Any]:
        return {
            "jobId": self.job_id,
            "durationSec": self.duration_sec,
            "analysisWindows": [
                {
                    "startSec": window.start_sec,
                    "endSec": window.end_sec,
                    "speechDensity": window.speech_density,
                    "silenceRatio": window.silence_ratio,
                    "emotionHits": window.emotion_hits,
                    "continuityScore": window.continuity_score,
                    "totalScore": window.total_score,
                }
                for window in self.analysis_windows
            ],
            "clipCandidates": [
                {
                    "startSec": candidate.start_sec,
                    "endSec": candidate.end_sec,
                    "score": candidate.score,
                    "transcriptExcerpt": candidate.transcript_excerpt,
                }
                for candidate in self.clip_candidates
            ],
        }
