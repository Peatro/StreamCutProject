# Legacy Notes Register

## Purpose

This document tracks repository notes that still exist for historical value but should not be treated as active project guidance.

It exists to reduce confusion without deleting older material.

---

## Usage Rule

If a document appears here, it is not an active source of truth unless it is explicitly promoted later.

Use these notes for: historical context, earlier product framing, old planning assumptions, external inspiration/reference.

Do not use them for: current task status, current architecture direction, current runtime behavior, or release-readiness decisions.

---

## Archived (moved out of the active surface)

These were relocated to `Documentation/archive/` on 2026-06-29 — they remain available as history but no longer sit beside active docs.

| Document | Classification | Why kept |
|---|---|---|
| `Documentation/archive/Main idea.md` | reference / historical | earliest product framing and concept description |
| `Documentation/archive/MVP Plan.md` | historical | earlier planning context, not the current roadmap |
| `Documentation/archive/HELP.md` | historical / generated | generated bootstrap scaffold (git-ignored), no project value |
| `Documentation/archive/v1.0.0-release.md` | historical | release record for the `v1.0.0` tag; current history lives in `CHANGELOG.md` |

## Reference (kept in place, not active source-of-truth)

| Document | Classification | Why kept |
|---|---|---|
| `Documentation/DESIGN.md` | reference | UI design-system reference material; not StreamCut architecture truth |

---

## Preferred Active Replacements

When current truth is needed, prefer:

- `CHANGELOG.md` — release history
- `Documentation/backlog.md` — status / roadmap
- `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md` — highlight-pipeline architecture
- `Documentation/architecture-roadmap.md` — post-`v1.0.0` direction
- `Documentation/runtime.md`, `Documentation/operations.md`, `Documentation/runbook.md` — runtime/operations
- `Documentation/doc-index.md` — the full map
