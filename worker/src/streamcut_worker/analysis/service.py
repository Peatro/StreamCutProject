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
    LoudnessProfile,
)

_EMPHASIS_WORD_RE = re.compile(r"\b[A-Z]{2,}\b")

# --- Scoring weight constants (tunable without code surgery) ---
HOOK_LEAD_IN_SEC: float = 0.4
WEIGHT_LOUDNESS: float = 0.45
WEIGHT_EMOTION: float = 0.20
WEIGHT_CONTINUITY: float = 0.20
WEIGHT_SILENCE: float = 0.15

# Fallback weights when loudness data is unavailable (preserves prior formula).
_FALLBACK_WEIGHT_SPEECH_DENSITY: float = 0.35
_FALLBACK_WEIGHT_SILENCE: float = 0.25
_FALLBACK_WEIGHT_EMOTION: float = 0.20
_FALLBACK_WEIGHT_CONTINUITY: float = 0.20


@dataclass(slots=True)
class SlidingWindowCandidateAnalysisService:
    window_duration_sec: float = 30.0
    step_sec: float = 5.0
    top_n: int | None = None
    min_overlap_ratio: float = 0.0
    hook_lead_in_sec: float = HOOK_LEAD_IN_SEC

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
                loudness_profile=request.loudness_profile,
            )
            for start_sec, end_sec in windows
        ]

        normalized_samples = self._normalize_windows(window_samples)
        candidates = self._select_candidates(
            normalized_samples,
            request.top_n,
            loudness_profile=request.loudness_profile,
            transcript_segments=request.transcript_segments,
            silence_segments=request.silence_segments,
        )

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
        loudness_profile: LoudnessProfile | None = None,
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

        # Compute per-window loudness: peak RMS dB among samples that fall
        # within the window.  None signals "no data available".
        raw_loudness: float | None = _window_peak_loudness(
            loudness_profile, start_sec, end_sec
        )

        return {
            "start_sec": start_sec,
            "end_sec": end_sec,
            "speech_density": speech_words / window_duration if window_duration > 0.0 else 0.0,
            "silence_ratio": min(1.0, silence_duration / window_duration) if window_duration > 0.0 else 0.0,
            "emotion_hits": emotion_hits,
            "continuity_score": min(1.0, longest_continuous_speech / window_duration) if window_duration > 0.0 else 0.0,
            "excerpt": _collapse_text(excerpt_segments),
            "raw_loudness": raw_loudness,
        }

    def _normalize_windows(
        self,
        raw_windows: list[dict[str, float | int | str]],
    ) -> list["_WindowSample"]:
        max_speech_density = max((float(window["speech_density"]) for window in raw_windows), default=0.0)
        max_emotion_hits = max((int(window["emotion_hits"]) for window in raw_windows), default=0)

        # Determine whether usable loudness data exists for this job.
        # "Usable" means at least one window has a non-None raw_loudness.
        raw_loudness_values: list[float] = [
            float(w["raw_loudness"])
            for w in raw_windows
            if w.get("raw_loudness") is not None
        ]
        has_loudness = len(raw_loudness_values) > 0

        if has_loudness:
            # Normalize raw dB values to [0, 1] across the job's windows.
            # RMS dB is negative (louder = closer to 0); shift so the loudest
            # window maps to 1.0 and the quietest to 0.0.
            min_loudness = min(raw_loudness_values)
            max_loudness = max(raw_loudness_values)
            loudness_range = max_loudness - min_loudness
        else:
            min_loudness = 0.0
            max_loudness = 0.0
            loudness_range = 0.0

        normalized: list[_WindowSample] = []
        for window in raw_windows:
            speech_density = float(window["speech_density"])
            silence_ratio = float(window["silence_ratio"])
            emotion_hits = int(window["emotion_hits"])
            continuity_score = float(window["continuity_score"])

            normalized_speech_density = speech_density / max_speech_density if max_speech_density > 0.0 else 0.0
            normalized_emotion_hits = emotion_hits / max_emotion_hits if max_emotion_hits > 0 else 0.0

            if has_loudness:
                raw_loud = window.get("raw_loudness")
                if raw_loud is not None:
                    normalized_loudness = (
                        (float(raw_loud) - min_loudness) / loudness_range
                        if loudness_range > 0.0
                        else 1.0
                    )
                else:
                    normalized_loudness = 0.0

                total_score = (
                    WEIGHT_LOUDNESS * normalized_loudness
                    + WEIGHT_EMOTION * normalized_emotion_hits
                    + WEIGHT_CONTINUITY * continuity_score
                    + WEIGHT_SILENCE * (1.0 - silence_ratio)
                )
            else:
                # Graceful degradation: fall back to the prior formula
                # when loudness data is entirely missing.
                normalized_loudness = 0.0
                total_score = (
                    _FALLBACK_WEIGHT_SPEECH_DENSITY * normalized_speech_density
                    + _FALLBACK_WEIGHT_SILENCE * (1.0 - silence_ratio)
                    + _FALLBACK_WEIGHT_EMOTION * normalized_emotion_hits
                    + _FALLBACK_WEIGHT_CONTINUITY * continuity_score
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
                        loudness=round(normalized_loudness, 6),
                    ),
                    transcript_excerpt=str(window["excerpt"]),
                )
            )

        return normalized

    def _select_candidates(
        self,
        windows: list["_WindowSample"],
        top_n: int | None,
        *,
        loudness_profile: LoudnessProfile | None = None,
        transcript_segments: list[TranscriptSegment] | None = None,
        silence_segments: list[SilenceInterval] | None = None,
    ) -> list[ClipCandidate]:
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

        candidates: list[ClipCandidate] = []
        for window in selected:
            w_start = window.analysis_window.start_sec
            w_end = window.analysis_window.end_sec

            shifted_start = _shift_start_to_peak(
                window_start=w_start,
                window_end=w_end,
                lead_in_sec=self.hook_lead_in_sec,
                loudness_profile=loudness_profile,
                transcript_segments=transcript_segments or [],
                silence_segments=silence_segments or [],
            )

            candidates.append(
                ClipCandidate(
                    start_sec=shifted_start,
                    end_sec=w_end,
                    score=window.analysis_window.total_score,
                    transcript_excerpt=window.transcript_excerpt,
                )
            )

        return candidates


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


def _window_peak_loudness(
    profile: LoudnessProfile | None,
    start_sec: float,
    end_sec: float,
) -> float | None:
    """Return the peak (max) RMS dB among profile samples within [start, end).

    Returns None when no profile exists or no samples fall within the window.
    """
    if profile is None or not profile.time_sec:
        return None

    peak: float | None = None
    for t, db in zip(profile.time_sec, profile.rms_db):
        if start_sec <= t < end_sec:
            if peak is None or db > peak:
                peak = db

    return peak


def _window_overlap_ratio(left: AnalysisWindow, right: AnalysisWindow) -> float:
    overlap = _interval_overlap(left.start_sec, left.end_sec, right.start_sec, right.end_sec)
    if overlap <= 0.0:
        return 0.0
    union = max(left.end_sec, right.end_sec) - min(left.start_sec, right.start_sec)
    return overlap / union if union > 0.0 else 0.0


def _window_peak_loudness_time(
    profile: LoudnessProfile | None,
    start_sec: float,
    end_sec: float,
) -> float | None:
    """Return the timestamp of the peak (max) RMS dB sample within [start, end).

    Returns None when no profile exists or no samples fall within the window.
    """
    if profile is None or not profile.time_sec:
        return None

    best_time: float | None = None
    best_db: float | None = None
    for t, db in zip(profile.time_sec, profile.rms_db):
        if start_sec <= t < end_sec:
            if best_db is None or db > best_db:
                best_db = db
                best_time = t

    return best_time


def _snap_to_boundary(
    target_sec: float,
    transcript_segments: list[TranscriptSegment],
    silence_segments: list[SilenceInterval],
    search_radius: float = 0.6,
) -> float:
    """Snap *target_sec* to the nearest word or silence boundary.

    Looks for the closest boundary (word start/end from transcript segments,
    or silence-interval start/end) within *search_radius* seconds of
    *target_sec*.  If no boundary is found within the radius the original
    *target_sec* is returned unchanged.
    """
    best: float | None = None
    best_dist: float = search_radius + 1.0  # sentinel > radius

    # Word-level boundaries from transcript segments.
    for seg in transcript_segments:
        # Skip segments that are entirely outside the search window.
        if seg.end_sec < target_sec - search_radius:
            continue
        if seg.start_sec > target_sec + search_radius:
            continue

        if seg.words:
            for w in seg.words:
                for edge in (w.start_sec, w.end_sec):
                    dist = abs(edge - target_sec)
                    if dist < best_dist:
                        best_dist = dist
                        best = edge
        else:
            # Fallback: use segment-level boundaries.
            for edge in (seg.start_sec, seg.end_sec):
                dist = abs(edge - target_sec)
                if dist < best_dist:
                    best_dist = dist
                    best = edge

    # Silence-interval boundaries (start/end of each silence gap).
    for sil in silence_segments:
        if sil.end_sec < target_sec - search_radius:
            continue
        if sil.start_sec > target_sec + search_radius:
            continue

        for edge in (sil.start_sec, sil.end_sec):
            dist = abs(edge - target_sec)
            if dist < best_dist:
                best_dist = dist
                best = edge

    return best if best is not None else target_sec


def _shift_start_to_peak(
    *,
    window_start: float,
    window_end: float,
    lead_in_sec: float,
    loudness_profile: LoudnessProfile | None,
    transcript_segments: list[TranscriptSegment],
    silence_segments: list[SilenceInterval],
) -> float:
    """Shift a heuristic candidate start toward the in-window loudness peak.

    1. Find the peak-loudness timestamp inside the window.
    2. Compute raw_start = peak - lead_in.
    3. Snap to the nearest word/silence boundary.
    4. Clamp so start >= 0, start >= window_start (never before window),
       and the resulting clip length stays within the original window length.

    Falls back to the original window_start when no loudness data is available.
    """
    peak_time = _window_peak_loudness_time(loudness_profile, window_start, window_end)
    if peak_time is None:
        return window_start

    raw_start = peak_time - lead_in_sec

    # Clamp: never before the window start and never below 0.
    raw_start = max(raw_start, window_start, 0.0)

    # Clamp: never past the peak itself (the lead-in should precede the peak).
    raw_start = min(raw_start, peak_time)

    # Snap to the nearest word/silence boundary.
    snapped = _snap_to_boundary(raw_start, transcript_segments, silence_segments)

    # Final clamp after snapping: stay within [window_start, peak_time] and >= 0.
    snapped = max(snapped, window_start, 0.0)
    snapped = min(snapped, peak_time)

    return round(snapped, 6)


@dataclass(frozen=True, slots=True)
class _WindowSample:
    analysis_window: AnalysisWindow
    transcript_excerpt: str
