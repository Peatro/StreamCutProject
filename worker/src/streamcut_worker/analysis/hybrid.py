"""Hybrid LLM + heuristic highlight detection (TASK-089).

Walks the transcript in overlapping chunks, feeds each chunk's text and
heuristic hints (loudness peaks, emotion hits, silence boundaries) to the
local Qwen model, and parses structured highlight moments.  Merges/dedupes
across overlapping chunks, then maps to ``ClipCandidate``s.

When the LLM is unavailable or detection errors, falls back to the existing
heuristic ``analyze_candidates`` so the pipeline never hard-fails.

Chunking math
-------------
Target: a 3-hour VOD (~10 800 s) should cost TENS of LLM calls, not hundreds.

  - Chunk size:  10 minutes (600 s) of transcript.
  - Overlap:     2 minutes (120 s) on each boundary.
  - Effective step: 600 - 120 = 480 s.
  - Calls for 3 h:  ceil((10800 - 600) / 480) + 1 = ceil(10200/480) + 1
                   = 22 + 1 = 23 calls.

23 calls is well within "tens" and gives enough context per chunk for the
LLM to understand conversational flow while the 2-min overlap ensures
moments near a boundary are seen by two chunks.

Enable flag
-----------
Controlled by the ``HYBRID_DETECTION_ENABLED`` env var (default: false).
The caller (job_runner) also checks ``llm_client.is_available`` before
attempting the hybrid path.
"""

from __future__ import annotations

import json
import logging
import math
import os
import re
from dataclasses import dataclass, field
from typing import Callable, Sequence

from streamcut_worker.inference import LlmClient, LlmUnavailableError
from streamcut_worker.silence.models import SilenceInterval
from streamcut_worker.transcription.models import TranscriptSegment

from .models import (
    CandidateAnalysisRequest,
    CandidateAnalysisResult,
    ClipCandidate,
    LoudnessProfile,
)
from .service import (
    analyze_candidates,
    _count_emotion_hits,
    _interval_overlap,
    _segment_text_in_window,
)

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Chunking constants (see docstring for math)
# ---------------------------------------------------------------------------
CHUNK_DURATION_SEC: float = 600.0   # 10 minutes per chunk
CHUNK_OVERLAP_SEC: float = 120.0    # 2 minutes overlap
CHUNK_STEP_SEC: float = CHUNK_DURATION_SEC - CHUNK_OVERLAP_SEC  # 480 s

# LLM generation parameters
LLM_MAX_TOKENS: int = 1024
LLM_TEMPERATURE: float = 0.0  # greedy decode: reproducible detection (no sampling noise)

# Merge/dedupe: two moments with IoU above this are considered duplicates
MERGE_IOU_THRESHOLD: float = 0.3


def is_hybrid_enabled() -> bool:
    """Check whether hybrid detection is enabled via environment."""
    return os.getenv("HYBRID_DETECTION_ENABLED", "false").strip().lower() in (
        "1", "true", "yes",
    )


# ---------------------------------------------------------------------------
# Data types for intermediate LLM results
# ---------------------------------------------------------------------------

@dataclass(frozen=True, slots=True)
class LlmHighlightMoment:
    """A single highlight moment parsed from LLM output."""
    start_sec: float
    end_sec: float
    reason: str
    confidence: float


# ---------------------------------------------------------------------------
# Chunking
# ---------------------------------------------------------------------------

@dataclass(frozen=True, slots=True)
class TranscriptChunk:
    """A chunk of transcript segments with its time boundaries."""
    start_sec: float
    end_sec: float
    segments: list[TranscriptSegment]


def build_chunks(
    segments: list[TranscriptSegment],
    duration_sec: float,
    chunk_duration: float = CHUNK_DURATION_SEC,
    chunk_step: float = CHUNK_STEP_SEC,
) -> list[TranscriptChunk]:
    """Split transcript segments into overlapping time-based chunks.

    Each chunk covers ``chunk_duration`` seconds with a step of
    ``chunk_step``, producing an overlap of ``chunk_duration - chunk_step``
    seconds at each boundary.
    """
    if duration_sec <= 0 or not segments:
        return []

    chunks: list[TranscriptChunk] = []
    start = 0.0

    while start < duration_sec:
        end = min(start + chunk_duration, duration_sec)
        # Collect segments that overlap this chunk window
        chunk_segments = [
            seg for seg in segments
            if _interval_overlap(seg.start_sec, seg.end_sec, start, end) > 0
        ]
        chunks.append(TranscriptChunk(
            start_sec=start,
            end_sec=end,
            segments=chunk_segments,
        ))
        if end >= duration_sec:
            break
        start += chunk_step

    return chunks


# ---------------------------------------------------------------------------
# Hint extraction
# ---------------------------------------------------------------------------

def _extract_loudness_peaks(
    profile: LoudnessProfile | None,
    start_sec: float,
    end_sec: float,
    top_n: int = 5,
) -> list[tuple[float, float]]:
    """Return up to ``top_n`` loudest (time, rms_db) pairs in the window."""
    if profile is None or not profile.time_sec:
        return []

    samples = [
        (t, db)
        for t, db in zip(profile.time_sec, profile.rms_db)
        if start_sec <= t < end_sec
    ]
    # Sort by loudness descending (closer to 0 dB = louder)
    samples.sort(key=lambda pair: pair[1], reverse=True)
    return samples[:top_n]


def _extract_emotion_hits(
    segments: list[TranscriptSegment],
    emotion_keywords: tuple[str, ...],
    start_sec: float,
    end_sec: float,
) -> list[tuple[float, str]]:
    """Return (time, keyword/marker) pairs for emotion hits in the window."""
    hits: list[tuple[float, str]] = []
    for seg in segments:
        if _interval_overlap(seg.start_sec, seg.end_sec, start_sec, end_sec) <= 0:
            continue
        count = _count_emotion_hits(seg.text, emotion_keywords)
        if count > 0:
            hits.append((seg.start_sec, seg.text.strip()[:60]))
    return hits


def _extract_silence_boundaries(
    silence_segments: list[SilenceInterval],
    start_sec: float,
    end_sec: float,
) -> list[tuple[float, float]]:
    """Return silence intervals that overlap the chunk window."""
    boundaries: list[tuple[float, float]] = []
    for si in silence_segments:
        overlap = _interval_overlap(si.start_sec, si.end_sec, start_sec, end_sec)
        if overlap > 0:
            boundaries.append((
                max(si.start_sec, start_sec),
                min(si.end_sec, end_sec),
            ))
    return boundaries


# ---------------------------------------------------------------------------
# Prompt construction
# ---------------------------------------------------------------------------

def build_chunk_prompt(
    chunk: TranscriptChunk,
    loudness_peaks: list[tuple[float, float]],
    emotion_hits: list[tuple[float, str]],
    silence_boundaries: list[tuple[float, float]],
) -> str:
    """Build the LLM prompt for a single transcript chunk.

    The prompt includes:
      - The chunk transcript with timestamps
      - Heuristic hints (loudness peaks, emotion hits, silence boundaries)
      - Instructions for structured output
    """
    # Build transcript text with timestamps
    transcript_lines: list[str] = []
    for seg in chunk.segments:
        transcript_lines.append(
            f"[{seg.start_sec:.1f}s - {seg.end_sec:.1f}s] {seg.text.strip()}"
        )
    transcript_text = "\n".join(transcript_lines) if transcript_lines else "(no speech in this segment)"

    # Build hints section
    hints_parts: list[str] = []

    if loudness_peaks:
        peak_lines = [f"  - {t:.1f}s: {db:.1f} dB" for t, db in loudness_peaks]
        hints_parts.append("LOUDNESS PEAKS (loudest moments in this chunk):\n" + "\n".join(peak_lines))

    if emotion_hits:
        emotion_lines = [f"  - {t:.1f}s: \"{text}\"" for t, text in emotion_hits]
        hints_parts.append("EMOTION/EXCITEMENT MARKERS:\n" + "\n".join(emotion_lines))

    if silence_boundaries:
        silence_lines = [f"  - {s:.1f}s to {e:.1f}s" for s, e in silence_boundaries]
        hints_parts.append("SILENCE BOUNDARIES (natural segment breaks):\n" + "\n".join(silence_lines))

    hints_text = "\n\n".join(hints_parts) if hints_parts else "(no heuristic hints available)"

    return f"""You are a highlight detector for a livestream VOD. Your job is to identify moments worth clipping for short-form content (TikTok, YouTube Shorts, etc.).

This is a TALKATIVE stream: the streamer chats, jokes, and reacts almost constantly. Clip-worthy moments are very often VERBAL, not just loud action. Look for:
- Funny one-liners, witty remarks, punchlines (can be short, ~5s)
- Spicy or contrarian hot takes, strong/relatable opinions
- Self-deprecating jokes, funny rants, comebacks, banter with chat
- Genuine hype, surprising events, epic fails, emotional peaks, lucky moments

A moment can be clip-worthy on the strength of WHAT IS SAID alone — it does NOT need a loudness peak or excitement marker. The hints below are supplementary signals, never requirements.

Analyze the transcript chunk (from {chunk.start_sec:.0f}s to {chunk.end_sec:.0f}s).

TRANSCRIPT:
{transcript_text}

HEURISTIC HINTS (supplementary signals only — a quiet but funny line is still clip-worthy):
{hints_text}

Return your findings as a JSON array of highlight moments. Each moment should have:
- "start_sec": start time in seconds (float)
- "end_sec": end time in seconds (float)
- "reason": brief explanation of why this is clip-worthy (string)
- "confidence": how confident you are this is a good clip, from 0.0 to 1.0 (float)

Calibrate confidence honestly: 0.5-0.6 = mildly amusing, 0.7-0.8 = clearly clip-worthy, 0.9+ = standout moment of the stream. Do not inflate.

Rules:
- Only return moments within the chunk range ({chunk.start_sec:.0f}s to {chunk.end_sec:.0f}s).
- Each moment should be 5-60 seconds long, tightly bounded around the actual punchline/peak.
- A talkative chunk usually contains at least one clippable line. Return an empty array [] ONLY if the chunk is essentially filler — silence, pure menu/UI navigation, or routine narration with no notable line.
- Return ONLY the JSON array, no other text.

JSON:"""


# ---------------------------------------------------------------------------
# LLM output parsing (robust)
# ---------------------------------------------------------------------------

def parse_llm_highlights(raw_output: str) -> list[LlmHighlightMoment]:
    """Parse LLM output into highlight moments, tolerating format drift.

    Handles:
      - Clean JSON arrays
      - JSON wrapped in markdown code blocks
      - Extra text before/after the JSON
      - Missing or extra fields
    """
    text = raw_output.strip()

    # Try to extract a JSON array from the output
    # Strategy 1: find [ ... ] directly
    json_match = re.search(r'\[.*\]', text, re.DOTALL)
    if json_match:
        try:
            parsed = json.loads(json_match.group(0))
            if isinstance(parsed, list):
                return _moments_from_parsed(parsed)
        except json.JSONDecodeError:
            pass

    # Strategy 2: try the whole text as JSON
    try:
        parsed = json.loads(text)
        if isinstance(parsed, list):
            return _moments_from_parsed(parsed)
    except json.JSONDecodeError:
        pass

    # Strategy 3: extract from markdown code block
    code_block_match = re.search(r'```(?:json)?\s*(\[.*?\])\s*```', text, re.DOTALL)
    if code_block_match:
        try:
            parsed = json.loads(code_block_match.group(1))
            if isinstance(parsed, list):
                return _moments_from_parsed(parsed)
        except json.JSONDecodeError:
            pass

    # Strategy 4: try to find individual JSON objects and collect them
    obj_matches = re.findall(r'\{[^{}]+\}', text)
    if obj_matches:
        moments: list[LlmHighlightMoment] = []
        for obj_str in obj_matches:
            try:
                obj = json.loads(obj_str)
                moment = _moment_from_dict(obj)
                if moment is not None:
                    moments.append(moment)
            except (json.JSONDecodeError, KeyError, ValueError):
                continue
        return moments

    logger.warning("parse_llm_highlights: could not parse any highlights from LLM output")
    return []


def _moments_from_parsed(items: list) -> list[LlmHighlightMoment]:
    """Convert a list of dicts to LlmHighlightMoment, skipping malformed."""
    moments: list[LlmHighlightMoment] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        moment = _moment_from_dict(item)
        if moment is not None:
            moments.append(moment)
    return moments


def _moment_from_dict(d: dict) -> LlmHighlightMoment | None:
    """Try to extract a moment from a dict, tolerating key variations."""
    try:
        start = float(d.get("start_sec", d.get("startSec", d.get("start", -1))))
        end = float(d.get("end_sec", d.get("endSec", d.get("end", -1))))
        reason = str(d.get("reason", d.get("description", "")))
        confidence = float(d.get("confidence", d.get("score", 0.5)))

        if start < 0 or end < 0 or end <= start:
            return None

        confidence = max(0.0, min(1.0, confidence))
        return LlmHighlightMoment(
            start_sec=start,
            end_sec=end,
            reason=reason,
            confidence=confidence,
        )
    except (TypeError, ValueError):
        return None


# ---------------------------------------------------------------------------
# Merge/deduplicate across overlapping chunks
# ---------------------------------------------------------------------------

def merge_moments(
    moments: list[LlmHighlightMoment],
    iou_threshold: float = MERGE_IOU_THRESHOLD,
) -> list[LlmHighlightMoment]:
    """Merge temporally-overlapping moments from different chunks.

    When two moments have IoU above ``iou_threshold``, keep the one with
    higher confidence.  The result is sorted by start time.
    """
    if not moments:
        return []

    # Sort by confidence descending so we keep the best of each group
    sorted_moments = sorted(moments, key=lambda m: -m.confidence)
    kept: list[LlmHighlightMoment] = []

    for moment in sorted_moments:
        is_duplicate = False
        for existing in kept:
            iou = _moment_iou(moment, existing)
            if iou >= iou_threshold:
                is_duplicate = True
                break
        if not is_duplicate:
            kept.append(moment)

    # Sort by start time for output
    kept.sort(key=lambda m: m.start_sec)
    return kept


def _moment_iou(a: LlmHighlightMoment, b: LlmHighlightMoment) -> float:
    """Compute IoU (Intersection over Union) of two moments."""
    overlap = _interval_overlap(a.start_sec, a.end_sec, b.start_sec, b.end_sec)
    if overlap <= 0:
        return 0.0
    union = max(a.end_sec, b.end_sec) - min(a.start_sec, b.start_sec)
    return overlap / union if union > 0 else 0.0


# ---------------------------------------------------------------------------
# Convert to ClipCandidates
# ---------------------------------------------------------------------------

def moments_to_candidates(
    moments: list[LlmHighlightMoment],
    segments: list[TranscriptSegment],
    top_n: int | None = None,
) -> list[ClipCandidate]:
    """Convert merged LLM moments to ClipCandidate objects.

    Maps ``confidence`` to ``score``.  Builds ``transcript_excerpt`` from
    segments overlapping each moment.  ``reason`` is kept internal (not
    part of the ClipCandidate contract).
    """
    # Sort by confidence descending, then start_sec ascending
    ranked = sorted(moments, key=lambda m: (-m.confidence, m.start_sec))

    if top_n is not None:
        ranked = ranked[:top_n]

    candidates: list[ClipCandidate] = []
    for moment in ranked:
        # Build excerpt from overlapping transcript segments
        excerpt_parts: list[str] = []
        for seg in segments:
            if _interval_overlap(seg.start_sec, seg.end_sec, moment.start_sec, moment.end_sec) > 0:
                excerpt_parts.append(_segment_text_in_window(seg, moment.start_sec, moment.end_sec))
        excerpt = " ".join(excerpt_parts)
        # Collapse whitespace
        excerpt = " ".join(excerpt.split())

        candidates.append(ClipCandidate(
            start_sec=moment.start_sec,
            end_sec=moment.end_sec,
            score=round(moment.confidence, 6),
            transcript_excerpt=excerpt,
        ))

    return candidates


# ---------------------------------------------------------------------------
# Stage 2: cross-stream selection (optional, env-gated)
# ---------------------------------------------------------------------------

SELECTION_TOP_N_DEFAULT: int = 12


def _moment_excerpt(
    moment: LlmHighlightMoment,
    segments: list[TranscriptSegment],
    limit: int = 220,
) -> str:
    """Short transcript excerpt for a moment, for the selection prompt."""
    parts = [
        _segment_text_in_window(seg, moment.start_sec, moment.end_sec)
        for seg in segments
        if _interval_overlap(seg.start_sec, seg.end_sec, moment.start_sec, moment.end_sec) > 0
    ]
    return " ".join(" ".join(parts).split())[:limit]


def _build_selection_prompt(
    moments: list[LlmHighlightMoment],
    excerpts: list[str],
    target_n: int,
) -> str:
    listing = "\n".join(
        f'[{i}] {m.start_sec:.0f}s — reason: {m.reason} — said: "{ex}"'
        for i, (m, ex) in enumerate(zip(moments, excerpts))
    )
    return f"""You are choosing the final highlight clips for a livestream VOD.

A first-pass detector flagged the {len(moments)} candidate moments below. Pick the {target_n} BEST — the moments a viewer would most want to watch or share: the funniest lines, sharpest takes, biggest reactions, genuine hype. Drop filler, near-duplicates, and anything only mildly interesting.

CANDIDATES:
{listing}

Return ONLY a JSON array of the indices to keep, best first, at most {target_n}. Example: [4, 0, 11]
JSON:"""


def _parse_selection(raw: str, n: int) -> list[int]:
    match = re.search(r"\[[\d,\s]*\]", raw)
    nums = re.findall(r"\d+", match.group(0) if match else raw)
    kept: list[int] = []
    for x in nums:
        idx = int(x)
        if 0 <= idx < n and idx not in kept:
            kept.append(idx)
    return kept


def _rank_keep(
    moments: list[LlmHighlightMoment],
    segments: list[TranscriptSegment],
    llm_client: LlmClient,
    keep_n: int,
) -> list[LlmHighlightMoment]:
    """One LLM ranking call over a shortlist; return its ``keep_n`` picks.
    Falls back to confidence top-n if the model returns nothing usable."""
    if len(moments) <= keep_n:
        return moments
    excerpts = [_moment_excerpt(m, segments) for m in moments]
    raw = llm_client.generate(
        _build_selection_prompt(moments, excerpts, keep_n),
        max_tokens=256,
        temperature=0.0,
    )
    kept = _parse_selection(raw, len(moments))
    if not kept:
        return sorted(moments, key=lambda m: -m.confidence)[:keep_n]
    return [moments[i] for i in kept[:keep_n]]


def select_moments(
    moments: list[LlmHighlightMoment],
    segments: list[TranscriptSegment],
    llm_client: LlmClient,
    target_n: int,
    batch_size: int | None = None,
) -> list[LlmHighlightMoment]:
    """Stage-2 selection: rank the candidate shortlist down to ``target_n``.

    A single all-candidates call overflows context (~100 cands × ~140 Cyrillic
    tokens >> n_ctx 8192): the tail is truncated so the model only ever ranks
    the early (start-of-stream) candidates — measured recall 90%→20% with 6/12
    picks in the first 1600s of an 11700s stream. Instead, rank in time-ordered
    batches (``merged`` is sorted by start_sec) so every part of the stream is
    judged in a short, non-truncated prompt, then re-rank the survivors across
    batches down to ``target_n``."""
    if len(moments) <= target_n:
        return moments
    batch_size = batch_size or int(os.getenv("HYBRID_SELECTION_BATCH_SIZE", "20"))
    if len(moments) <= batch_size:
        return _rank_keep(moments, segments, llm_client, target_n)

    n_batches = math.ceil(len(moments) / batch_size)
    keep_per_batch = max(1, math.ceil(target_n * 2 / n_batches))
    survivors: list[LlmHighlightMoment] = []
    for start in range(0, len(moments), batch_size):
        batch = moments[start:start + batch_size]
        survivors.extend(_rank_keep(batch, segments, llm_client, keep_per_batch))
    logger.info(
        "hybrid_select batched n_batches=%d keep_per_batch=%d survivors=%d of %d",
        n_batches, keep_per_batch, len(survivors), len(moments),
    )
    if len(survivors) <= target_n:
        return survivors
    final = _rank_keep(survivors, segments, llm_client, target_n)
    logger.info("hybrid_select final kept=%d of %d survivors", len(final), len(survivors))
    return final


# ---------------------------------------------------------------------------
# Main hybrid detection entry point
# ---------------------------------------------------------------------------

def hybrid_detect(
    *,
    llm_client: LlmClient,
    request: CandidateAnalysisRequest,
) -> CandidateAnalysisResult:
    """Run hybrid LLM + heuristic highlight detection.

    For each overlapping transcript chunk:
      1. Extract heuristic hints (loudness peaks, emotion hits, silence).
      2. Build a prompt with the chunk transcript + hints.
      3. Call the LLM and parse structured highlight moments.

    Then merge/dedupe across chunks and convert to ClipCandidates.

    Raises ``LlmUnavailableError`` if the LLM client cannot serve.
    The caller is responsible for catching this and falling back.
    """
    duration_sec = request.duration_sec
    if duration_sec is None:
        transcript_max = max(
            (seg.end_sec for seg in request.transcript_segments), default=0.0
        )
        silence_max = max(
            (seg.end_sec for seg in request.silence_segments), default=0.0
        )
        duration_sec = max(transcript_max, silence_max)

    chunks = build_chunks(
        request.transcript_segments,
        duration_sec,
    )

    if not chunks:
        return CandidateAnalysisResult(
            job_id=request.job_id,
            duration_sec=duration_sec,
            analysis_windows=[],
            clip_candidates=[],
        )

    all_moments: list[LlmHighlightMoment] = []

    for i, chunk in enumerate(chunks):
        logger.info(
            "hybrid_detect chunk=%d/%d range=%.0f-%.0fs segments=%d",
            i + 1, len(chunks), chunk.start_sec, chunk.end_sec, len(chunk.segments),
        )

        # Extract hints for this chunk
        loudness_peaks = _extract_loudness_peaks(
            request.loudness_profile, chunk.start_sec, chunk.end_sec,
        )
        emotion_hits = _extract_emotion_hits(
            chunk.segments, request.emotion_keywords, chunk.start_sec, chunk.end_sec,
        )
        silence_boundaries = _extract_silence_boundaries(
            request.silence_segments, chunk.start_sec, chunk.end_sec,
        )

        # Build prompt
        prompt = build_chunk_prompt(
            chunk, loudness_peaks, emotion_hits, silence_boundaries,
        )

        # Call LLM (may raise LlmUnavailableError)
        raw_output = llm_client.generate(
            prompt,
            max_tokens=LLM_MAX_TOKENS,
            temperature=LLM_TEMPERATURE,
        )

        # Observability: surface what the model actually returned so a
        # parsed_moments=0 can be told apart from a parse failure.
        logger.info(
            "hybrid_detect chunk=%d/%d llm_raw len=%d preview=%r",
            i + 1, len(chunks), len(raw_output), raw_output[:600],
        )

        # Parse
        moments = parse_llm_highlights(raw_output)
        logger.info(
            "hybrid_detect chunk=%d/%d parsed_moments=%d",
            i + 1, len(chunks), len(moments),
        )
        all_moments.extend(moments)

    # Merge/dedupe across overlapping chunks
    merged = merge_moments(all_moments)
    logger.info(
        "hybrid_detect merged_moments=%d (from %d raw across %d chunks)",
        len(merged), len(all_moments), len(chunks),
    )

    # Stage 2: optional cross-stream selection pass (env-gated). The per-chunk
    # recall prompt over-produces; this ranks the whole shortlist down to the
    # strongest few.
    if os.getenv("HYBRID_SELECTION_ENABLED", "false").strip().lower() in ("1", "true", "yes"):
        target_n = int(os.getenv("HYBRID_SELECTION_TOP_N", str(SELECTION_TOP_N_DEFAULT)))
        merged = select_moments(merged, request.transcript_segments, llm_client, target_n)
        logger.info("hybrid_detect after_selection=%d (target_n=%d)", len(merged), target_n)

    # Convert to ClipCandidates
    candidates = moments_to_candidates(
        merged, request.transcript_segments, top_n=request.top_n,
    )

    # Unload LLM after detection to free VRAM for next job's whisper
    try:
        llm_client.unload()
    except Exception:
        logger.warning("hybrid_detect: failed to unload LLM", exc_info=True)

    return CandidateAnalysisResult(
        job_id=request.job_id,
        duration_sec=duration_sec,
        analysis_windows=[],  # Hybrid path does not produce heuristic windows
        clip_candidates=candidates,
    )


def analyze_candidates_hybrid(
    request: CandidateAnalysisRequest,
    llm_client: LlmClient | None = None,
    heuristic_fallback: Callable[[CandidateAnalysisRequest], CandidateAnalysisResult] | None = None,
) -> CandidateAnalysisResult:
    """Top-level entry point with automatic fallback.

    Tries the hybrid LLM path if:
      - ``HYBRID_DETECTION_ENABLED`` is true
      - ``llm_client`` is provided and ``is_available``

    On any failure (``LlmUnavailableError``, parse errors, etc.),
    falls back to the existing heuristic ``analyze_candidates``.

    Args:
        heuristic_fallback: Optional callable to use for the heuristic path.
            When provided (e.g. by the job runner), this is called instead
            of the module-level ``analyze_candidates`` so the runner's own
            analysis service instance (which may be faked in tests) is used.
    """
    fallback = heuristic_fallback or analyze_candidates

    if (
        is_hybrid_enabled()
        and llm_client is not None
        and llm_client.is_available
    ):
        try:
            logger.info("analyze_candidates_hybrid: attempting hybrid LLM detection for job=%s", request.job_id)
            return hybrid_detect(llm_client=llm_client, request=request)
        except LlmUnavailableError:
            logger.warning(
                "analyze_candidates_hybrid: LLM unavailable for job=%s, falling back to heuristic",
                request.job_id,
                exc_info=True,
            )
        except Exception:
            logger.error(
                "analyze_candidates_hybrid: unexpected error for job=%s, falling back to heuristic",
                request.job_id,
                exc_info=True,
            )

    logger.info("analyze_candidates_hybrid: using heuristic fallback for job=%s", request.job_id)
    return fallback(request)
