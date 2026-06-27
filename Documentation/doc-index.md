# Documentation Index

## Purpose

This file is the navigation map for project documentation.

It exists to answer three questions quickly:

1. where a document lives
2. what it is for
3. whether it is a source-of-truth document or a mirror/reference document

---

## Source Of Truth Policy

Repository documentation is canonical.

Primary rule:

- update repository docs first
- update Obsidian mirrors second

If a document exists both in the repository and in Obsidian, the repository version wins unless explicitly stated otherwise.

---

## Core Documents

| Document | Purpose | Source Of Truth | Mirror |
|---|---|---|---|
| `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md` | authoritative architecture of the highlight-detection / clip-selection / scoring / fusion pipeline (detection vs judgment layers, LLM role, arithmetic fusion, epistemic status) | repository | none required |
| `Documentation/clip-quality-plan.md` | clip-quality leverage ladder and remaining hook/subtitle/resize work; defers to the architecture brief for highlight-pipeline decisions | repository | none required |
| `Documentation/backlog.md` | project status, release track, task queue, roadmap status | repository | Obsidian backlog mirror |
| `README.md` | repository landing page with overview, quick start, and entry points to deeper docs | repository | none required |
| `Documentation/documentarian-role.md` | documentation ownership and sync rules | repository | `Documentarian Role.md` |
| `Documentation/doc-index.md` | documentation map and ownership guide | repository | `Doc Index.md` |
| `Documentation/architecture-roadmap.md` | post-`v1.0.0` architecture direction and sequencing | repository | `Architecture Roadmap.md` |
| `Documentation/runtime.md` | actual runtime assumptions and operator-facing runtime contract | repository | none required yet |
| `Documentation/alerts.md` | sample Prometheus alert rules for the runtime metrics surface | repository | none required yet |
| `Documentation/release-checklist.md` | release gate document | repository | none required yet |
| `Documentation/STORAGE.md` | storage behavior and constraints | repository | none required yet |
| `Documentation/worker-scaling-roadmap.md` | worker/runtime scaling direction | repository | may be reflected in architecture notes |
| `agents/contracts/*.md` | current agent-facing technical contracts | repository | no full mirror; keep repo-first |
| `agents/roles/*.md` | current agent operating roles | repository | no full mirror; keep repo-first |
| `agents/global/*.md` | current agent operating rules and workflow | repository | no full mirror; keep repo-first |
| `agents/templates/*.md` | current task, review, and prompt templates | repository | no full mirror; keep repo-first |

---

## Supporting Documents

| Document | Purpose | Notes |
|---|---|---|
| `Documentation/DESIGN.md` | general design notes | keep aligned with current architecture |
| `Documentation/docker-compose.md` | runtime packaging notes | operational reference |
| `Documentation/Main idea.md` | product and concept framing | may contain early ideas; verify against current implementation |
| `Documentation/MVP Plan.md` | historical planning context | useful for history, not current source of truth |
| `Documentation/HELP.md` | auxiliary reference | review for current relevance |
| Obsidian `Агенты/Agents Starter PAck.md` | original starter-pack note for the agent system | reference only; do not treat as canonical once repo `agents/` exists |

---

## ADR Directory

Path:

- `Documentation/adr/`

Purpose:

- record durable architectural decisions
- capture reasoning that should not be buried in backlog text

Template:

- `Documentation/adr/ADR-TEMPLATE.md`

Use ADRs when:

- a decision changes architecture or runtime shape
- the team may revisit the same question later
- the cost of losing rationale is high

---

## Document Update Rules

### Update `backlog.md` when:

- task status changes
- new `TASK-0xx` files are created
- roadmap sequence changes
- release gate status changes

### Update `architecture-roadmap.md` when:

- post-`v1.0.0` sequence changes
- execution model or worker topology direction changes
- queue/storage/runtime architecture changes

### Create or update an ADR when:

- a durable technical decision is made
- a major architecture tradeoff is resolved
- the project chooses one long-lived direction over another

### Update agent operating docs when:

- the execution model changes
- worker/backend transport semantics change
- orchestration rules change
- the task template or review discipline changes
- role responsibilities drift from actual codebase direction

---

## Recommended Near-Term ADR Candidates

- task-centric execution model vs job-centric orchestration
- export-worker split from processing-worker
- signed URLs and backend media delivery policy
- object storage as durable artifact contract
- queue delivery abstraction before broker migration

---

## Maintenance Notes

The documentarian should keep this index short and current.

If a document is obsolete:

- remove it
- archive it
- or mark it explicitly as historical

Do not let the index become a graveyard of forgotten notes.

---

## Current Registry Addendum

The current active documentation registry also includes:

- `Documentation/operations.md`
  operator-facing operational model and monitoring/recovery overview

- `Documentation/alerts.md`
  sample alert rules for the actuator and Prometheus metrics surface

- `Documentation/adr/ADR-001-repo-first-document-sync.md`
  canonical rule for repository-first documentation sync

- `Documentation/adr/ADR-002-task-centric-execution-model.md`
  architectural decision that execution semantics belong in the task layer

- `agents/contracts/*.md`, `agents/roles/*.md`, `agents/global/*.md`, and `agents/templates/*.md`
  repository-canonical agent operating system for implementation work

If older sections in this file use legacy mirror naming, prefer this addendum and the repository/Obsidian sync policy from `Documentation/documentarian-role.md`.

---

## Normalized Mirror Registry

Use this registry when mirror naming in older sections is inconsistent.

| Repository Document | Mirror / Note |
|---|---|
| `Documentation/backlog.md` | Obsidian backlog mirror |
| `Documentation/documentarian-role.md` | `Documentarian Role.md` |
| `Documentation/doc-index.md` | `Doc Index.md` |
| `Documentation/architecture-roadmap.md` | `Architecture Roadmap.md` |
| `Documentation/operations.md` | `Operations Guide.md` |
| `Documentation/documentation-debt.md` | `Documentation Debt Register.md` |
| `agents/*` operating documents | no full mirror; repo-first |

---

## ADR Registry

- `Documentation/adr/ADR-001-repo-first-document-sync.md`
- `Documentation/adr/ADR-002-task-centric-execution-model.md`

Near-term likely ADRs:

- export-worker split from processing-worker
- signed URL delivery policy
- object storage as durable artifact contract
- queue delivery abstraction before broker migration

---

## Document Lifecycle Registry

### Active

- `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md`
- `Documentation/clip-quality-plan.md`
- `Documentation/backlog.md`
- `Documentation/doc-index.md`
- `Documentation/documentarian-role.md`
- `Documentation/architecture-roadmap.md`
- `Documentation/runtime.md`
- `Documentation/alerts.md`
- `Documentation/operations.md`
- `Documentation/release-checklist.md`
- `Documentation/STORAGE.md`
- `Documentation/worker-scaling-roadmap.md`
- `Documentation/documentation-debt.md`
- `Documentation/legacy-notes.md`

### Reference

- `Documentation/DESIGN.md`
- `Documentation/Main idea.md`

### Historical

- `Documentation/MVP Plan.md`
- `Documentation/HELP.md`

Lifecycle notes:

- active documents should be kept aligned with implementation reality
- reference documents may contain useful context but are not current source-of-truth docs
- historical documents should not be used to infer current status or architecture direction

---

## Legacy And Archive Controls

- `Documentation/legacy-notes.md`
  registry of files that still exist but should not be treated as active guidance

- `Documentation/archive/README.md`
  policy for moving older documents out of the active docs surface when needed

- `Documentation/documentation-debt.md`
  tracks cleanup work still needed before some legacy documents can be safely normalized or archived

---

## Obsidian Task Archive

The Obsidian vault now contains a mirrored task archive for repository `TASK-*.md` files.

Primary notes:

- Obsidian `Архив задач/TASK Archive Index.md`
- Obsidian `Архив задач/TASK Archive Table.md`

Archive groups:

- `01 Completed`
- `02 In Progress`
- `03 Planned v1.0.0`
- `04 Planned post-v1.0.0`

This archive is a navigation mirror only.

Task definitions remain canonical in:

- `agents/tasks/`

Task statuses remain canonical in:

- `Documentation/backlog.md`

## Agent Operating System

The repository `agents/` tree is the canonical operating system for agent-driven implementation work.

Canonical repository paths:

- `agents/global/*.md`
- `agents/roles/*.md`
- `agents/contracts/*.md`
- `agents/templates/*.md`
- `agents/tasks/*.md`

Mirror policy:

- task definitions may be mirrored into the Obsidian task archive for navigation
- the rest of the agent operating docs remain repository-first unless an explicit mirror is later introduced
- the Obsidian starter-pack note under `Агенты/Agents Starter PAck.md` is reference-only and should not be treated as current truth
