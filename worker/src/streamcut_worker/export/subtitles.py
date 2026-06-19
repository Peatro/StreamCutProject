"""ASS subtitle generator for word-by-word karaoke captions.

Generates styled ASS (Advanced SubStation Alpha) subtitles with \\k karaoke
timing so the active word highlights in sync with speech.  Words are grouped
into readable phrases (configurable max words per line) rather than displayed
one at a time.

Style constants are module-level for easy tuning without code surgery.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from .models import WordTiming

# ---------------------------------------------------------------------------
# Configurable style constants
# ---------------------------------------------------------------------------

# Font / size
CAPTION_FONT_NAME: str = "Arial"
CAPTION_FONT_SIZE: int = 56
CAPTION_BOLD: bool = True

# Colors (ASS &HBBGGRR& format, alpha is &HAA&)
# Primary: white text for upcoming words
CAPTION_PRIMARY_COLOR: str = "&H00FFFFFF"
# Secondary: highlight color for the active karaoke word (yellow)
CAPTION_SECONDARY_COLOR: str = "&H0000FFFF"
# Outline color: black for contrast
CAPTION_OUTLINE_COLOR: str = "&H00000000"
# Back/shadow color
CAPTION_BACK_COLOR: str = "&H80000000"

# Outline thickness and shadow
CAPTION_OUTLINE: int = 3
CAPTION_SHADOW: int = 1

# Positioning: MarginV controls distance from the reference edge.
# Alignment 8 = top-center.  We use alignment 2 (bottom-center) with a
# generous MarginV to sit ABOVE the platform UI safe-zone (~18% from bottom).
CAPTION_ALIGNMENT: int = 2
CAPTION_MARGIN_V: int = 200
CAPTION_MARGIN_L: int = 40
CAPTION_MARGIN_R: int = 40

# Vertical-reframe override: when burning into a 9:16 canvas the captions
# must sit inside the foreground safe-zone.  The foreground is vertically
# centered so the safe-zone bottom is roughly canvas_h/2 + fg_h/2.
# We push MarginV higher to stay above the platform chrome in the 9:16 frame.
CAPTION_MARGIN_V_REFRAME: int = 340

# Phrase grouping
MAX_WORDS_PER_PHRASE: int = 5

# Playback resolution (ASS header); doesn't affect burn, just style scaling.
ASS_PLAY_RES_X: int = 1920
ASS_PLAY_RES_Y: int = 1080


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

@dataclass(frozen=True, slots=True)
class _ClipWord:
    """A word with clip-relative timestamps (0-based)."""
    word: str
    start_sec: float
    end_sec: float


@dataclass(frozen=True, slots=True)
class _Phrase:
    """A group of words displayed together as one subtitle event."""
    words: list[_ClipWord]
    start_sec: float
    end_sec: float


def _offset_words_to_clip(
    words: Sequence[WordTiming],
    clip_start: float,
    clip_end: float,
) -> list[_ClipWord]:
    """Filter words to [clip_start, clip_end] and offset to clip-relative time."""
    result: list[_ClipWord] = []
    for w in words:
        # Word must overlap the clip window
        if w.end_sec <= clip_start or w.start_sec >= clip_end:
            continue
        # Clamp to clip boundaries
        ws = max(w.start_sec, clip_start) - clip_start
        we = min(w.end_sec, clip_end) - clip_start
        if we <= ws:
            continue
        result.append(_ClipWord(word=w.word.strip(), start_sec=ws, end_sec=we))
    return result


def _group_into_phrases(
    words: list[_ClipWord],
    max_per_phrase: int = MAX_WORDS_PER_PHRASE,
) -> list[_Phrase]:
    """Group words into readable on-screen phrases."""
    if not words:
        return []

    phrases: list[_Phrase] = []
    chunk: list[_ClipWord] = []

    for w in words:
        chunk.append(w)
        if len(chunk) >= max_per_phrase:
            phrases.append(_Phrase(
                words=list(chunk),
                start_sec=chunk[0].start_sec,
                end_sec=chunk[-1].end_sec,
            ))
            chunk = []

    if chunk:
        phrases.append(_Phrase(
            words=list(chunk),
            start_sec=chunk[0].start_sec,
            end_sec=chunk[-1].end_sec,
        ))

    return phrases


def _format_ass_time(seconds: float) -> str:
    """Format seconds as ASS timestamp: H:MM:SS.cc (centiseconds)."""
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = seconds % 60
    cs = int(round((s - int(s)) * 100))
    return f"{h}:{m:02d}:{int(s):02d}.{cs:02d}"


def _build_karaoke_text(phrase: _Phrase) -> str:
    r"""Build the ASS dialogue text with \k karaoke tags.

    Each word gets a \k<cs> prefix where <cs> is the word's duration in
    centiseconds.  The effect: libass renders upcoming words in PrimaryColour,
    then fills them with SecondaryColour (karaoke highlight) as time passes.

    We use \kf (fill / smooth highlight) for a nicer visual.
    """
    parts: list[str] = []
    for w in phrase.words:
        dur_cs = max(1, int(math.ceil((w.end_sec - w.start_sec) * 100)))
        parts.append(f"{{\\kf{dur_cs}}}{w.word}")
    return " ".join(parts)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def generate_ass_content(
    words: Sequence[WordTiming],
    clip_start: float,
    clip_end: float,
    *,
    vertical_reframe: bool = False,
    max_words_per_phrase: int = MAX_WORDS_PER_PHRASE,
) -> str | None:
    """Generate ASS subtitle content from word timings.

    Returns the full ASS file content as a string, or None if no words
    fall within the clip window (graceful no-op).
    """
    clip_words = _offset_words_to_clip(words, clip_start, clip_end)
    if not clip_words:
        return None

    phrases = _group_into_phrases(clip_words, max_per_phrase=max_words_per_phrase)
    if not phrases:
        return None

    margin_v = CAPTION_MARGIN_V_REFRAME if vertical_reframe else CAPTION_MARGIN_V

    header = (
        "[Script Info]\n"
        "ScriptType: v4.00+\n"
        f"PlayResX: {ASS_PLAY_RES_X}\n"
        f"PlayResY: {ASS_PLAY_RES_Y}\n"
        "WrapStyle: 0\n"
        "ScaledBorderAndShadow: yes\n"
        "\n"
        "[V4+ Styles]\n"
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
        "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
        "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
        "Alignment, MarginL, MarginR, MarginV, Encoding\n"
        f"Style: Karaoke,{CAPTION_FONT_NAME},{CAPTION_FONT_SIZE},"
        f"{CAPTION_PRIMARY_COLOR},{CAPTION_SECONDARY_COLOR},"
        f"{CAPTION_OUTLINE_COLOR},{CAPTION_BACK_COLOR},"
        f"{-1 if CAPTION_BOLD else 0},0,0,0,"
        f"100,100,0,0,1,{CAPTION_OUTLINE},{CAPTION_SHADOW},"
        f"{CAPTION_ALIGNMENT},{CAPTION_MARGIN_L},{CAPTION_MARGIN_R},{margin_v},1\n"
        "\n"
        "[Events]\n"
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
    )

    events: list[str] = []
    for phrase in phrases:
        start_ts = _format_ass_time(phrase.start_sec)
        end_ts = _format_ass_time(phrase.end_sec)
        text = _build_karaoke_text(phrase)
        events.append(
            f"Dialogue: 0,{start_ts},{end_ts},Karaoke,,0,0,0,,{text}"
        )

    return header + "\n".join(events) + "\n"


def write_ass_file(
    words: Sequence[WordTiming],
    clip_start: float,
    clip_end: float,
    output_path: Path,
    *,
    vertical_reframe: bool = False,
) -> Path | None:
    """Generate and write an ASS subtitle file.

    Returns the output path if subtitles were generated, None otherwise
    (graceful no-op when no words are available).
    """
    content = generate_ass_content(
        words, clip_start, clip_end, vertical_reframe=vertical_reframe,
    )
    if content is None:
        return None

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(content, encoding="utf-8")
    return output_path
