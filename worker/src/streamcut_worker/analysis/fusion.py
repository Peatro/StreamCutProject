"""Arithmetic rank-fusion over the candidate pool — the precision layer.

Single source of truth: ``Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md`` §4.
One timeline, N scorers, ONE additive rank-fusion, human cuts. This module does
the FUSION only: it contains no LLM and makes no detection decisions. Detection
(recall) happens upstream; this layer reorders the pool for precision.

Invariants from the brief — do not violate:
  * Combine RANKS, not raw scores. Scorers (chat density, loudness dB, LLM
    worthiness) live on incomparable scales; raw addition would let one axis
    accidentally dominate. Ranks normalize every scorer to the same scale.
  * ADDITIVE, never multiplicative. Addition = voting: a candidate strong on one
    axis and silent on the others still rises. Multiplication = veto: any factor
    near zero zeros the product, which kills the best "strong in one axis" clips
    (a deadpan hot take = high text, flat audio, quiet chat -> text*audio*chat ~ 0).
    Multiplication is forbidden here.
  * "More important" = bigger WEIGHT, not multiplication. w_text = w_audio > w_chat.
  * w_chat auto-scales with chat volume (``chat_weight``): a tiny audience lets the
    chat axis go quiet and text/audio dominate; a lively chat earns a full vote.
    One pipeline adapts to audience size with no manual retuning.
  * rank-ALL: return the FULL reordered pool, never cut to a fixed N. Cut-to-N
    converts a ranking error into permanent recall loss (proven failure mode:
    select-to-N collapsed to random). The human does the cutting.

Audio note: "audio" here is loudness only (the DSP the pipeline already produces).
Laughter / pitch are future DSP axes; when added they become additional scorers
with their own weight, no change to the fusion math.
"""

from __future__ import annotations

import bisect
from typing import Mapping, Sequence

# Chat volume at which the chat axis earns a full vote. Below it, w_chat scales
# down linearly. ponytail: a guess (~a few hundred msgs/stream is "lively" for a
# small streamer); calibrate once there are 2-3 labeled streams.
CHAT_FULL_VOTE_MSGS = 500

# Default fusion weights. Audio and text are co-equal primaries; chat is a
# secondary whose effective weight is further scaled by volume (see chat_weight).
WEIGHT_TEXT = 1.0
WEIGHT_AUDIO = 1.0
WEIGHT_CHAT = 1.0


def chat_weight(total_chat_msgs: int, full_vote_msgs: int = CHAT_FULL_VOTE_MSGS) -> float:
    """w_chat = min(1, total/full_vote). Quiet chat -> small vote; lively -> full."""
    if full_vote_msgs <= 0:
        return 1.0
    return min(1.0, max(0, total_chat_msgs) / full_vote_msgs)


def _ranks(scores: Sequence[float]) -> list[float]:
    """Map scores to ranks where the BEST (highest) score gets rank 1.0.

    Ties share the average of the ranks they span (so a block of equal scores
    can't bias the fusion by their order). Lower fused rank = better.
    """
    n = len(scores)
    order = sorted(range(n), key=lambda i: scores[i], reverse=True)
    ranks = [0.0] * n
    i = 0
    while i < n:
        j = i
        while j + 1 < n and scores[order[j + 1]] == scores[order[i]]:
            j += 1
        avg = (i + j) / 2.0 + 1.0  # ranks are 1-based; average over the tie block
        for k in range(i, j + 1):
            ranks[order[k]] = avg
        i = j + 1
    return ranks


def fuse(
    scorers: Mapping[str, Sequence[float]],
    weights: Mapping[str, float],
) -> list[int]:
    """Fuse per-candidate scores into one ranked order (best first).

    Args:
        scorers: name -> per-candidate score list. Every list must be the same
            length (one entry per candidate). A scorer that has no data for this
            stream should simply be omitted (drop the whole axis), not zero-filled.
        weights: name -> weight. Names absent from ``scorers`` are ignored.

    Returns:
        Candidate indices ordered best-first. ALL candidates are returned
        (rank-all): the caller shows the whole pool reordered; the human cuts.
    """
    if not scorers:
        raise ValueError("fuse() needs at least one scorer")
    lengths = {len(v) for v in scorers.values()}
    if len(lengths) != 1:
        raise ValueError(f"all scorers must have equal length, got {lengths}")
    n = lengths.pop()
    if n == 0:
        return []

    ranked = {name: _ranks(scores) for name, scores in scorers.items()}
    fused = [0.0] * n
    for name, rk in ranked.items():
        w = weights.get(name, 0.0)
        for i in range(n):
            fused[i] += w * rk[i]  # ADDITIVE — never multiply (see module docstring)

    # Lower fused rank-sum = better. Stable tie-break by index keeps order
    # deterministic. rank-ALL: every index is returned.
    return sorted(range(n), key=lambda i: (fused[i], i))


# ---------------------------------------------------------------------------
# Non-LLM scorers (modality axes that bypass Whisper)
# ---------------------------------------------------------------------------

def chat_density_scores(
    centers: Sequence[float],
    chat_offsets_sec: Sequence[float],
    half_window_sec: float = 30.0,
) -> list[float]:
    """Per-candidate chat-message count within +-half_window of its center.

    Measured signal (fixture 21): chat density separates hand-labels from random
    points and catches the non-verbal gacha climax that the text axis misses.
    Raw volume conflates highlight reactions with viewer side-chat, so this is a
    fusion VOTER, not a standalone ranker.
    """
    offs = sorted(chat_offsets_sec)
    out = []
    for c in centers:
        lo = bisect.bisect_left(offs, c - half_window_sec)
        hi = bisect.bisect_right(offs, c + half_window_sec)
        out.append(float(hi - lo))
    return out


def audio_loudness_scores(
    centers: Sequence[float],
    loudness_time_sec: Sequence[float],
    loudness_rms_db: Sequence[float],
    half_window_sec: float = 30.0,
    silence_floor_db: float = -99.0,
) -> list[float]:
    """Per-candidate mean RMS dB within +-half_window (ignoring pure silence).

    Measured signal (fixture 21): mean-loudness AUC ~0.66 — a weak but real voter
    (louder windows correlate with reactions/action). Blind to quiet verbal jokes
    (the text axis covers those); that complementarity is the point of fusion.
    A window with only silence returns the floor (ranks worst).
    """
    t = list(loudness_time_sec)
    out = []
    for c in centers:
        lo = bisect.bisect_left(t, c - half_window_sec)
        hi = bisect.bisect_right(t, c + half_window_sec)
        vals = [d for d in loudness_rms_db[lo:hi] if d > silence_floor_db]
        out.append(sum(vals) / len(vals) if vals else silence_floor_db)
    return out
