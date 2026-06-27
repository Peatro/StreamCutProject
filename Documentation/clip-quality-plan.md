# Clip Quality Plan — Priority Ladder For "A Good Clip"

Last updated: 2026-06-19
Status: planning (tasks TASK-087..093 drafted, not yet implemented)
Driver: operator direction 2026-06-19 — "quality" must have an internal priority, or it dissolves into infinite polish (dangerous for a perfectionist). Order the work by leverage; the brain is moment-selection, everything else is plumbing around it.

> **Source of truth for the highlight pipeline:** `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md`.
> This plan's leverage ladder, hook/subtitle/resize work, and the ground-truth guardrail still hold, but several
> highlight-architecture decisions below have been **superseded** by the brief. In particular: detection and
> judgment are now two distinct layers (detection = "is something here?", recall; judgment/fusion = "is this a good
> clip?", precision); the LLM is a *recall* detector plus one fusion voter, never a ranker/selector; loudness and
> chat are separate orthogonal fusion voters, not prompt hints feeding a single hybrid gate; selection is rank-all
> over the full pool (NMS-deduped), not a top-N or confidence-sorted cut. Where this plan and the brief disagree,
> the brief wins. Superseded points are flagged inline below.

## Why this document exists
"Make the clips better" is unbounded. This plan fixes a **priority order by leverage** so effort goes where it changes the product, and a **guardrail metric** so "better" is measured, not felt. Scarce interesting engineering time goes to the top of the ladder; the bottom is solved-problem plumbing that must not absorb attention.

## The leverage ladder (most important first)

1. **Moment selection — the brain.** A perfectly reframed boring clip is still boring. This is the one part a dumb ffmpeg cropper can never replicate. Silence segmentation gives clean boundaries (already present); **moment selection decides what is even worth cutting** (new). This is where the deficit engineering time goes. (Per the brief, "moment selection" is really *two* layers: detection answers "is something here?" and optimizes recall — it floods a candidate pool on purpose; judgment/fusion then answers "is this a good clip?" and optimizes precision by re-ranking that pool. Do not collapse them into one step.)
2. **The hook — first 1–2 seconds.** Short-form dies on the first second. The clip must open **on the action**, not on the run-up to it. The trimming logic shifts the start toward the peak instead of including the wind-up. Almost free on top of detection, disproportionately high payoff.
3. **Subtitles — burned-in, readable on mute.** Positioned above the platform UI safe-zone (the very bottom is covered by the app's chrome). faster-whisper already produces word timings; we render styled captions.
4. **Resize — Tier 0 blurred-fill.** A solved technique. Vertical 9:16 with a blurred background fill. Do it, do not spend brain on it.

## The guardrail: ground-truth before tuning
Without a small hand-labeled ground-truth set, threshold tuning is blind and "improvement" is vanecdote. The operator has ~4 years of stream data and knows what landed (long streams, My Summer Car, Thursdays). The honest signal that a month was not wasted is **the detector's hit-rate against a hand-picked set**, not "feels better".

**Rule: build the ground-truth harness (TASK-087) before tuning the detector (TASK-089).** Hand-label a dozen-plus moments the operator would personally cut; measure detector overlap against them every change.

## Decisions (locked 2026-06-19)

| Decision | Choice | Rationale |
|---|---|---|
| Highlight detector deployment | **Local Qwen on the GPU worker** | Single-operator self-host: private (transcript never leaves the box), no per-call cost, no network/rate-limit dependency inside the pipeline. Sits alongside the existing GPU whisper path. |
| Detector role vs heuristic | **Hybrid: LLM walks transcript chunks; loudness/emotion peaks are fed as hints in the prompt** ⚠️ *superseded — see brief §1, §3, §4* | Keeps recall (catches quiet-but-good moments the heuristic would miss) without LLM-ing every 5s window of a 3h VOD. **Current architecture:** the LLM detector walks the transcript purely for recall ("is something here?"); loudness/audio and chat-density are **not** prompt hints into one hybrid gate but **separate orthogonal fusion voters** combined arithmetically downstream (`score = w_text·rank_text + w_audio·rank_audio + w_chat·rank_chat`, additive ranks, never multiplied). Keeping the signals separate is the point — chat/audio bypass Whisper and cover its blind spots, which an in-prompt hint cannot. |
| Subtitle style | **Word-by-word karaoke** | Higher short-form engagement. Requires per-word timings (see below). |
| Word timings | **Stop discarding them; persist end-to-end** | faster-whisper already computes word timestamps (`word_timestamps=True`); the transcript model throws them away, keeping only `word_count`. Karaoke needs them at export time, so persist them through transport + storage rather than re-transcribing the clip. |

### Defaults baked in (not separately asked)
- **Detector input:** transcript chunk text + injected heuristic hints (loudness peak times/levels, emotion hits, silence boundaries in-chunk). Not multimodal (audio/video frames) in v1 — revisit only if text+loudness underperforms on the ground-truth metric.
- **Hook lead-in:** shift start to the action peak minus a small configurable pre-roll (default ~0.4s); never cut mid-word — snap to the nearest word/silence boundary; respect min/max clip length.
- **Ground-truth format:** JSON `[{source, start_sec, end_sec, note}]` under `worker/eval/ground_truth/`; metric = temporal hit-rate (a labeled moment counts as hit if a detector candidate overlaps it past an IoU/center threshold) plus a false-positive count. Operator populates labels by hand.

## Open / contentious points (tracked, not blocking)
- **Recall ceiling of hybrid detection.** Even hybrid chunk-walking can miss a great moment if chunk boundaries split it. Mitigation: overlapping chunks. If ground-truth recall stays low, escalate toward full-transcript single-pass or multimodal — decide *from the metric*, not taste.
- **GPU contention.** Qwen and whisper share the GPU worker. If they fight for VRAM, sequence them per job (transcribe → unload → detect) rather than co-resident. Flagged in TASK-088.
- **Karaoke export cost.** TASK-080 just bounded export CPU. Burning per-word ASS adds encode cost; TASK-092 must keep the export within the bounded-threads budget and measure it.
- **Candidate `reason`/`confidence` surfacing.** The LLM produces a reason + confidence; v1 maps confidence→existing `score` and keeps `reason` internal. Surfacing `reason` in the review UI is a later, optional follow-up (would touch the candidate contract). ⚠️ *Superseded — see brief §4:* detector `confidence` is **proven random with respect to clip quality** (sorting top-30 by confidence kept only 5/10 true positives vs 9/10 for the full pool), so it must **not** be the candidate sort key. The shipped floor (commit `93c73cc`) replaced top-N-by-score with temporal NMS (w=45s, dedup-for-display only) and rank-all; the eventual ordering key is the arithmetic fusion, not confidence.

## Task map and execution order

Leverage order is detection > hook > subtitles > resize, but **TASK-087 runs first** because it gates honest tuning of detection.

| Task | Title | Agent | Depends on |
|---|---|---|---|
| TASK-087 | Ground-truth clip set + detector evaluation harness | worker | — (do first) |
| TASK-088 | Local Qwen inference serving in the GPU worker (infra) | worker/infra | — |
| TASK-089 | Hybrid LLM highlight detection logic | worker | 088, measured by 087 |
| TASK-090 | Hook: shift clip start to the action peak | worker | — (builds on existing loudness/silence) |
| TASK-091 | Persist word-level transcript timings end-to-end | backend + worker | — (enables 092) |
| TASK-092 | Word-by-word karaoke burned-in subtitles at export | worker | 091 |
| TASK-093 | Vertical reframe Tier 0 blurred-fill at export | worker | — |

Everything else in the prior roadmap holds. The brain is detection; the rest is harness around it.
