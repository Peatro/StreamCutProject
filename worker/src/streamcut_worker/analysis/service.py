from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Iterable

from streamcut_worker.silence.models import SilenceInterval
from streamcut_worker.transcription.models import TranscriptSegment

from .models import (
    AnalysisWindow,
    CandidateAnalysisRequest,
    CandidateAnalysisResult,
    ClipCandidate,
)

_EMPHASIS_WORD_RE = re.compile(r"\b[A-Z]{2,}\b")


@dataclass(slots=True)
class SlidingWindowCandidateAnalysisService:
    window_duration_sec: float = 30.0
    step_sec: float = 5.0
    top_n: int | None = None
    min_overlap_ratio: float = 0.0

    def analyze(self, request: CandidateAnalysisRequest) -> CandidateAnalysisResult:
        duration_sec = self._resolve_duration(request)
        windows = self._build_windows(request, duration_sec)

        window_samples = [
            self._build_window_metrics(
                transcript_segments=request.transcript_segments,
                silence_segments=request.silence_segments,
                start_sec=start_sec,
                end_sec=end_sec,
                emotion_keywords=request.emotion_keywords,
            )
            for start_sec, end_sec in windows
        ]

        normalized_samples = self._normalize_windows(window_samples)
        candidates = self._select_candidates(normalized_samples, request.top_n)

        return CandidateAnalysisResult(
            job_id=request.job_id,
            duration_sec=duration_sec,
            analysis_windows=[sample.analysis_window for sample in normalized_samples],
            clip_candidates=candidates,
        )

    def _resolve_duration(self, request: CandidateAnalysisRequest) -> float:
        if request.duration_sec is not None:
            return max(0.0, float(request.duration_sec))

        transcript_max = max((segment.end_sec for segment in request.transcript_segments), default=0.0)
        silence_max = max((segment.end_sec for segment in request.silence_segments), default=0.0)
        return max(transcript_max, silence_max)

    def _build_windows(
        self,
        request: CandidateAnalysisRequest,
        duration_sec: float,
    ) -> list[tuple[float, float]]:
        window_duration = max(0.0, float(request.window_duration_sec or self.window_duration_sec))
        step = max(0.1, float(request.step_sec or self.step_sec))

        if duration_sec <= 0.0:
            return []

        if duration_sec <= window_duration:
            return [(0.0, duration_sec)]

        last_start = max(0.0, duration_sec - window_duration)
        windows: list[tuple[float, float]] = []
        current_start = 0.0
        while current_start < last_start:
            windows.append((current_start, current_start + window_duration))
            current_start += step

        if not windows or windows[-1][0] != last_start:
            windows.append((last_start, last_start + window_duration))

        return windows

    def _build_window_metrics(
        self,
        *,
        transcript_segments: list[TranscriptSegment],
        silence_segments: list[SilenceInterval],
        start_sec: float,
        end_sec: float,
        emotion_keywords: tuple[str, ...],
    ) -> dict[str, float | int | str]:
        window_duration = max(end_sec - start_sec, 0.0)
        speech_words = 0.0
        silence_duration = 0.0
        emotion_hits = 0
        speech_intervals: list[tuple[float, float]] = []
        excerpt_segments: list[str] = []

        for segment in transcript_segments:
            overlap = _interval_overlap(segment.start_sec, segment.end_sec, start_sec, end_sec)
            if overlap <= 0.0:
                continue

            excerpt_segments.append(segment.text.strip())
            segment_duration = max(segment.end_sec - segment.start_sec, 0.0)
            if segment_duration > 0.0:
                speech_words += segment.word_count * (overlap / segment_duration)
            else:
                speech_words += float(segment.word_count)

            speech_intervals.append((max(segment.start_sec, start_sec), min(segment.end_sec, end_sec)))
            emotion_hits += _count_emotion_hits(segment.text, emotion_keywords)

        for silence_segment in silence_segments:
            silence_duration += _interval_overlap(
                silence_segment.start_sec,
                silence_segment.end_sec,
                start_sec,
                end_sec,
            )

        longest_continuous_speech = _longest_interval_length(_merge_intervals(speech_intervals))

        return {
            "start_sec": start_sec,
            "end_sec": end_sec,
            "speech_density": speech_words / window_duration if window_duration > 0.0 else 0.0,
            "silence_ratio": min(1.0, silence_duration / window_duration) if window_duration > 0.0 else 0.0,
            "emotion_hits": emotion_hits,
            "continuity_score": min(1.0, longest_continuous_speech / window_duration) if window_duration > 0.0 else 0.0,
            "excerpt": _collapse_text(excerpt_segments),
        }

    def _normalize_windows(
        self,
        raw_windows: list[dict[str, float | int | str]],
    ) -> list["_WindowSample"]:
        max_speech_density = max((float(window["speech_density"]) for window in raw_windows), default=0.0)
        max_emotion_hits = max((int(window["emotion_hits"]) for window in raw_windows), default=0)

        normalized: list[_WindowSample] = []
        for window in raw_windows:
            speech_density = float(window["speech_density"])
            silence_ratio = float(window["silence_ratio"])
            emotion_hits = int(window["emotion_hits"])
            continuity_score = float(window["continuity_score"])

            normalized_speech_density = speech_density / max_speech_density if max_speech_density > 0.0 else 0.0
            normalized_emotion_hits = emotion_hits / max_emotion_hits if max_emotion_hits > 0 else 0.0
            total_score = (
                0.35 * normalized_speech_density
                + 0.25 * (1.0 - silence_ratio)
                + 0.20 * normalized_emotion_hits
                + 0.20 * continuity_score
            )

            normalized.append(
                _WindowSample(
                    analysis_window=AnalysisWindow(
                        start_sec=float(window["start_sec"]),
                        end_sec=float(window["end_sec"]),
                        speech_density=speech_density,
                        silence_ratio=silence_ratio,
                        emotion_hits=emotion_hits,
                        continuity_score=continuity_score,
                        total_score=round(total_score, 6),
                    ),
                    transcript_excerpt=str(window["excerpt"]),
                )
            )

        return normalized

    def _select_candidates(self, windows: list["_WindowSample"], top_n: int | None) -> list[ClipCandidate]:
        if top_n is not None and top_n <= 0:
            return []

        selected: list[_WindowSample] = []
        for window in sorted(
            windows,
            key=lambda item: (
                -item.analysis_window.total_score,
                item.analysis_window.start_sec,
                item.analysis_window.end_sec,
            ),
        ):
            if any(
                _window_overlap_ratio(window.analysis_window, chosen.analysis_window) > self.min_overlap_ratio
                for chosen in selected
            ):
                continue
            selected.append(window)
            if top_n is not None and len(selected) >= top_n:
                break

        return [
            ClipCandidate(
                start_sec=window.analysis_window.start_sec,
                end_sec=window.analysis_window.end_sec,
                score=window.analysis_window.total_score,
                transcript_excerpt=window.transcript_excerpt,
            )
            for window in selected
        ]


def analyze_candidates(request: CandidateAnalysisRequest) -> CandidateAnalysisResult:
    service = SlidingWindowCandidateAnalysisService(
        window_duration_sec=request.window_duration_sec,
        step_sec=request.step_sec,
        top_n=request.top_n,
        min_overlap_ratio=request.min_overlap_ratio,
    )
    return service.analyze(request)


def _count_emotion_hits(text: str, keywords: tuple[str, ...]) -> int:
    if not text:
        return 0

    normalized_text = text.lower()
    hits = 0
    for keyword in keywords:
        keyword = keyword.strip().lower()
        if not keyword:
            continue
        hits += len(re.findall(rf"\b{re.escape(keyword)}\b", normalized_text))

    hits += text.count("!")
    hits += len(_EMPHASIS_WORD_RE.findall(text))
    return hits


def _collapse_text(parts: Iterable[str]) -> str:
    text = " ".join(part.strip() for part in parts if part.strip())
    return " ".join(text.split())


def _interval_overlap(start_a: float, end_a: float, start_b: float, end_b: float) -> float:
    return max(0.0, min(end_a, end_b) - max(start_a, start_b))


def _merge_intervals(intervals: list[tuple[float, float]]) -> list[tuple[float, float]]:
    if not intervals:
        return []

    sorted_intervals = sorted(intervals, key=lambda item: (item[0], item[1]))
    merged: list[tuple[float, float]] = [sorted_intervals[0]]

    for start, end in sorted_intervals[1:]:
        last_start, last_end = merged[-1]
        if start <= last_end:
            merged[-1] = (last_start, max(last_end, end))
        else:
            merged.append((start, end))

    return merged


def _longest_interval_length(intervals: list[tuple[float, float]]) -> float:
    return max((end - start for start, end in intervals), default=0.0)


def _window_overlap_ratio(left: AnalysisWindow, right: AnalysisWindow) -> float:
    overlap = _interval_overlap(left.start_sec, left.end_sec, right.start_sec, right.end_sec)
    if overlap <= 0.0:
        return 0.0
    union = max(left.end_sec, right.end_sec) - min(left.start_sec, right.start_sec)
    return overlap / union if union > 0.0 else 0.0


@dataclass(frozen=True, slots=True)
class _WindowSample:
    analysis_window: AnalysisWindow
    transcript_excerpt: str
