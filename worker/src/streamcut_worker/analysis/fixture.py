"""Freeze/thaw a detector input (``CandidateAnalysisRequest``) to JSON.

The evaluation harness needs a *fixed* detector input so tuning is reproducible
regardless of download/transcription jitter (the same Twitch VOD downloads at a
slightly different length each run → a different transcript → different output).

Capture once from a real job (set ``STREAMCUT_FREEZE_ANALYSIS_DIR`` and run it),
then the harness replays the frozen request through the detector forever.
"""

from __future__ import annotations

import json
from pathlib import Path

from streamcut_worker.silence.models import SilenceInterval
from streamcut_worker.transcription.models import TranscriptSegment, TranscriptWord

from .models import CandidateAnalysisRequest, LoudnessProfile


def request_to_dict(request: CandidateAnalysisRequest) -> dict:
    return {
        "job_id": request.job_id,
        "duration_sec": request.duration_sec,
        "window_duration_sec": request.window_duration_sec,
        "step_sec": request.step_sec,
        "top_n": request.top_n,
        "min_overlap_ratio": request.min_overlap_ratio,
        "emotion_keywords": list(request.emotion_keywords),
        "transcript_segments": [
            {
                "start_sec": s.start_sec,
                "end_sec": s.end_sec,
                "text": s.text,
                "word_count": s.word_count,
                "words": [
                    {"word": w.word, "start_sec": w.start_sec, "end_sec": w.end_sec}
                    for w in s.words
                ],
            }
            for s in request.transcript_segments
        ],
        "silence_segments": [
            {"start_sec": s.start_sec, "end_sec": s.end_sec, "duration_sec": s.duration_sec}
            for s in request.silence_segments
        ],
        "loudness_profile": (
            {
                "time_sec": list(request.loudness_profile.time_sec),
                "rms_db": list(request.loudness_profile.rms_db),
            }
            if request.loudness_profile is not None
            else None
        ),
    }


def request_from_dict(data: dict) -> CandidateAnalysisRequest:
    loudness = data.get("loudness_profile")
    return CandidateAnalysisRequest(
        job_id=data["job_id"],
        transcript_segments=[
            TranscriptSegment(
                start_sec=s["start_sec"],
                end_sec=s["end_sec"],
                text=s["text"],
                word_count=s["word_count"],
                words=[
                    TranscriptWord(word=w["word"], start_sec=w["start_sec"], end_sec=w["end_sec"])
                    for w in s.get("words", [])
                ],
            )
            for s in data["transcript_segments"]
        ],
        silence_segments=[
            SilenceInterval(start_sec=s["start_sec"], end_sec=s["end_sec"], duration_sec=s["duration_sec"])
            for s in data["silence_segments"]
        ],
        duration_sec=data.get("duration_sec"),
        window_duration_sec=data.get("window_duration_sec", 30.0),
        step_sec=data.get("step_sec", 5.0),
        top_n=data.get("top_n"),
        min_overlap_ratio=data.get("min_overlap_ratio", 0.0),
        emotion_keywords=tuple(data.get("emotion_keywords", [])),
        loudness_profile=(
            LoudnessProfile(time_sec=tuple(loudness["time_sec"]), rms_db=tuple(loudness["rms_db"]))
            if loudness is not None
            else None
        ),
    )


def dump_request(request: CandidateAnalysisRequest, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(request_to_dict(request), ensure_ascii=False, indent=2), encoding="utf-8")


def load_request(path: Path) -> CandidateAnalysisRequest:
    return request_from_dict(json.loads(Path(path).read_text(encoding="utf-8")))
