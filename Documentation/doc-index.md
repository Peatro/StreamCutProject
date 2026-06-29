# Documentation Index

The navigation map for project documentation. It answers: where a document
lives, what it is for, and its lifecycle status.

**Source-of-truth policy:** the repository is canonical. Some docs are mirrored
to Obsidian for navigation; if they ever disagree, the repository wins. Update
the repo doc first, the mirror second. See `documentarian-role.md`.

---

## Map

### Source of truth — architecture & status

| Document | Purpose |
|---|---|
| `README.md` | repository landing page: overview, quick start, doc entry points |
| `CHANGELOG.md` | release history (versions and what changed) |
| `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md` | **authoritative** highlight pipeline: detection vs judgment layers, LLM role, arithmetic fusion, epistemic status |
| `Documentation/architecture-roadmap.md` | post-`v1.0.0` architecture direction, execution/topology/storage sequencing |
| `Documentation/clip-quality-plan.md` | clip-quality leverage ladder + hook/subtitle/resize work (defers to the brief for pipeline decisions) |
| `Documentation/backlog.md` | project status, release track, task queue, roadmap status |

### Process & documentation governance

| Document | Purpose |
|---|---|
| `Documentation/doc-index.md` | this map |
| `Documentation/documentarian-role.md` | documentation ownership and repo/Obsidian sync rules |
| `Documentation/documentation-debt.md` | register of stale/missing/conflicting docs and cleanup work |
| `Documentation/release-checklist.md` | controlled release gate (develop → main) |
| `Documentation/legacy-notes.md` | register of archived/reference docs not to be treated as active |

### Operations & runtime

| Document | Purpose |
|---|---|
| `Documentation/runtime.md` | runtime contract and operator-facing assumptions (incl. upload policy) |
| `Documentation/operations.md` | operator operational model, monitoring/recovery overview |
| `Documentation/runbook.md` | step-by-step operator procedures (backup, restore, upgrade) |
| `Documentation/STORAGE.md` | storage layout and constraints |
| `Documentation/alerts.md` | sample Prometheus alert rules for the metrics surface |
| `Documentation/docker-compose.md` | runtime packaging / compose notes |
| `Documentation/worker-scaling-roadmap.md` | worker/runtime scaling direction |

### Reference

| Document | Purpose |
|---|---|
| `Documentation/DESIGN.md` | UI design-system reference (Linear dark system) — not architecture truth |

### Archive — historical, do NOT infer current state

| Document | Was |
|---|---|
| `Documentation/archive/v1.0.0-release.md` | v1.0.0 release notes (history now in `CHANGELOG.md`) |
| `Documentation/archive/MVP Plan.md` | early planning context |
| `Documentation/archive/Main idea.md` | earliest product/concept framing |
| `Documentation/archive/HELP.md` | generated bootstrap scaffold (git-ignored) |
| `Documentation/archive/README.md` | archive policy |

### Decision records

`Documentation/adr/` — durable architectural decisions. Template:
`adr/ADR-TEMPLATE.md`. Current: ADR-001 (repo-first doc sync), ADR-002
(task-centric execution model). Add an ADR when a decision changes
architecture/runtime shape and the rationale must not be lost.

### Agent operating system

`agents/` is the repository-canonical operating system for agent-driven work:
`agents/global/*` (rules/workflow/architecture), `agents/roles/*`,
`agents/contracts/*`, `agents/templates/*`, `agents/tasks/*` (task definitions —
statuses are canonical in `backlog.md`). Task defs may be mirrored to the
Obsidian task archive for navigation; everything else is repo-first.

---

## When to update what

- **`backlog.md`** — task status changes, new `TASK-0xx`, roadmap sequence or release-gate status changes.
- **`architecture-roadmap.md`** — post-`v1.0.0` sequence, execution model, worker topology, or queue/storage/runtime architecture changes.
- **`STREAMCUT_ARCHITECTURE_BRIEF.md`** — any change to detection/judgment/fusion design or its measured/epistemic status.
- **`CHANGELOG.md`** — every release (and notable unreleased changes under `[Unreleased]`).
- **An ADR** — a durable technical decision or major tradeoff is resolved.
- **agent operating docs** — execution model, transport semantics, orchestration rules, templates, or role responsibilities drift from the codebase.
- **`documentation-debt.md`** — a doc is found stale/misleading, a structural issue is fixed, or a doc changes lifecycle (active → archived).

## Maintenance

Keep this index short and current. When a document becomes obsolete, archive it
(`Documentation/archive/`) and update its row here — do not let the index become
a graveyard of forgotten notes.
