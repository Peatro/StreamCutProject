from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Iterable, Protocol, Sequence

from .exceptions import TranscriptionException
from .models import TranscriptSegment, TranscriptWord, TranscriptionRequest, TranscriptionResult


class WhisperWordLike(Protocol):
    start: float
    end: float
    word: str


class WhisperSegmentLike(Protocol):
    start: float
    end: float
    text: str
    words: Sequence[WhisperWordLike] | None


class WhisperInfoLike(Protocol):
    language: str | None
    language_probability: float | None
    duration: float | None


class WhisperModelLike(Protocol):
    def transcribe(
        self,
        audio: str,
        *,
        beam_size: int,
        language: str | None,
        vad_filter: bool,
        word_timestamps: bool,
        condition_on_previous_text: bool,
    ) -> tuple[Iterable[WhisperSegmentLike], WhisperInfoLike]:
        raise NotImplementedError


@dataclass(slots=True)
class FasterWhisperTranscriptionService:
    model: WhisperModelLike
    model_name: str = "unknown"

    def transcribe(
        self,
        request: TranscriptionRequest,
        on_progress: Callable[[float, float], None] | None = None,
    ) -> TranscriptionResult:
        if not request.audio_path.exists():
            raise TranscriptionException(
                f"Input audio does not exist: {request.audio_path}",
                audio_path=str(request.audio_path),
                model_name=self.model_name,
            )

        try:
            segments, info = self.model.transcribe(
                str(request.audio_path),
                beam_size=request.beam_size,
                language=request.language,
                vad_filter=request.vad_filter,
                word_timestamps=True,
                condition_on_previous_text=False,
            )
        except Exception as exc:  # pragma: no cover - defensive wrapping
            raise TranscriptionException(
                "faster-whisper failed while transcribing audio",
                audio_path=str(request.audio_path),
                model_name=self.model_name,
                stderr=str(exc),
            ) from exc

        total_duration = float(info.duration) if info.duration is not None else None
        transcript_segments: list[TranscriptSegment] = []

        for segment in segments:
            transcript_segments.append(TranscriptSegment(
                start_sec=float(segment.start),
                end_sec=float(segment.end),
                text=segment.text.strip(),
                word_count=_count_words(segment),
                words=_extract_words(segment),
            ))
            if on_progress is not None and total_duration is not None and total_duration > 0:
                on_progress(min(float(segment.end), total_duration), total_duration)

        duration_sec = _resolve_duration(info.duration, transcript_segments)
        if on_progress is not None and duration_sec > 0:
            on_progress(duration_sec, duration_sec)

        return TranscriptionResult(
            job_id=request.job_id,
            audio_path=request.audio_path,
            duration_sec=duration_sec,
            language=info.language,
            language_probability=info.language_probability,
            transcript_segments=transcript_segments,
        )


def create_default_transcription_service(
    *,
    model_size: str = "large-v3-turbo",
    device: str = "cpu",
    compute_type: str = "int8",
) -> FasterWhisperTranscriptionService:
    try:
        from faster_whisper import WhisperModel
    except ImportError as exc:  # pragma: no cover - runtime dependency error
        raise TranscriptionException(
            "faster-whisper is not installed",
            model_name=model_size,
            stderr=str(exc),
        ) from exc

    model = WhisperModel(model_size, device=device, compute_type=compute_type)
    return FasterWhisperTranscriptionService(model=model, model_name=model_size)


def _count_words(segment: WhisperSegmentLike) -> int:
    words = getattr(segment, "words", None)
    if words:
        return len(words)

    text = segment.text.strip()
    if not text:
        return 0
    return len(text.split())


def _extract_words(segment: WhisperSegmentLike) -> list[TranscriptWord]:
    words = getattr(segment, "words", None)
    if not words:
        return []
    return [
        TranscriptWord(
            word=w.word.strip(),
            start_sec=float(w.start),
            end_sec=float(w.end),
        )
        for w in words
    ]


def _resolve_duration(duration: float | None, segments: list[TranscriptSegment]) -> float:
    if duration is not None:
        return float(duration)
    if not segments:
        return 0.0
    return max(segment.end_sec for segment in segments)
