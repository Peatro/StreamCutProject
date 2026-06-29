# Documentation Debt Register

## Purpose

This document tracks documentation gaps, stale notes, contradictory docs, and cleanup work needed to keep the documentation system trustworthy.

Use it for:

- stale or misleading documents
- missing docs for implemented behavior
- conflicting source-of-truth situations
- structure cleanup tasks that are not product work but still matter

---

## Status Levels

- `open`
- `in_progress`
- `resolved`
- `accepted`

---

## Open Items

### DOC-001 Legacy mirror naming and encoding artifacts

- Status: `resolved`
- Area: repository docs
- Problem:
  some repository documents still contain mojibake around the Obsidian backlog mirror name
- Risk:
  low functional risk, medium trust/readability cost
- Fix:
  active repository docs now use normalized mirror naming and no longer depend on mojibake-prone mirror labels

### DOC-002 `Documentation/DESIGN.md` is not a project design source

- Status: `resolved`
- Area: legacy/reference docs
- Problem:
  the file contains unrelated UI design-system reference material and should not be mistaken for StreamCut architecture truth
- Fix:
  classified `reference` in `legacy-notes.md` and the doc index (kept in place — frontend work references it); no longer listed as an active source-of-truth doc

### DOC-003 `Documentation/HELP.md` is generated scaffold text

- Status: `resolved`
- Area: legacy/reference docs
- Problem:
  the file is generic scaffold documentation and does not represent meaningful project guidance
- Fix:
  moved to `Documentation/archive/HELP.md` (it is git-ignored, so it only ever existed in the working tree); out of the active docs surface

### DOC-004 `Documentation/Main idea.md` is historical and encoding-damaged in terminal output

- Status: `resolved`
- Area: product/history docs
- Problem:
  the document appears useful as historical product framing, but it should not be treated as an active source of truth and its current encoding presentation is unreliable in terminal output
- Risk:
  medium confusion risk
- Fix:
  verified UTF-8 readability with explicit console encoding and added explicit historical/reference status header
- Current note:
  direct in-place patching is risky until file encoding is normalized for tooling-safe edits

### DOC-005 `Documentation/MVP Plan.md` is historical planning context, not current plan

- Status: `resolved`
- Area: legacy/planning docs
- Problem:
  the file represents earlier planning context and can easily be confused with current roadmap or backlog truth
- Risk:
  medium confusion risk
- Fix:
  verified UTF-8 readability with explicit console encoding and added explicit historical status header
- Current note:
  direct in-place patching is risky until file encoding is normalized for tooling-safe edits

### DOC-011 Legacy Cyrillic docs need a dedicated encoding-normalization pass

- Status: `accepted`
- Area: legacy docs
- Problem:
  some older UTF-8 Cyrillic files render as mojibake in current terminal/tooling paths, making safe incremental patching unreliable
- Risk:
  medium maintenance risk
- Current stance:
  safe UTF-8 reads and targeted edits are now confirmed for the currently needed legacy files, so a broad normalization pass is no longer urgent
- Remaining value:
  if more legacy Cyrillic notes need bulk maintenance later, run a focused normalization pass at that time

### DOC-006 Operations and runtime boundaries are still lightweight

- Status: `open`
- Area: active docs
- Problem:
  `operations.md` now exists, but operational procedures are still summarized at a high level and need deeper cross-links as runtime behavior matures
- Risk:
  medium operator-readiness risk
- Intended fix:
  expand operations guidance together with `TASK-059` through `TASK-063`

---

### DOC-012 Highlight-pipeline docs aligned to the architecture brief

- Status: `resolved`
- Area: architecture docs
- Problem:
  several docs described the highlight pipeline with stale framing (LLM as ranker/selector, top-N / confidence-sorted
  candidate cut, "hybrid" prompt-hint gate, audio treated as an unmeasured given) that contradicts
  `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md` (the single source of truth for detection/judgment/fusion)
- Fix:
  registered the brief and `clip-quality-plan.md` in `doc-index.md`; added source-of-truth pointers from
  `architecture-roadmap.md`, `agents/global/architecture.md`, and the historical `Main idea.md` / `MVP Plan.md`
  banners (whose top-N body is preserved as history). `clip-quality-plan.md` and `backlog.md` already carried the
  brief pointer and supersede notes from the prior pass
- Remaining note:
  the brief's §6 open question — an *independent* human ground truth (current `worker/eval/ground_truth/21.json` is
  model-ratified recognition, not independent labeling) — is product/R&D work, not documentation debt; tracked in the
  brief, not here. The fusion engine commit (`e535af7`, `worker/.../analysis/fusion.py`, not yet wired) is likewise
  documented only in the brief

### DOC-013 Documentation surface restructured (archive + lean index)

- Status: `resolved`
- Area: documentation structure
- Problem:
  the active `Documentation/` surface had accumulated historical docs beside current ones, and `doc-index.md` had grown several overlapping registries (ADR and mirror registries listed twice, "Current Registry Addendum" / "Normalized Mirror Registry" / "Document Lifecycle Registry" layered over Core/Supporting) — the index had become the graveyard it warns against
- Fix:
  moved the historical docs (`Main idea.md`, `MVP Plan.md`, `HELP.md`, `v1.0.0-release.md`) into `Documentation/archive/` and fixed inbound links (README, release-checklist, backlog, legacy-notes); rewrote `doc-index.md` into one lean lifecycle-grouped map (Source-of-truth / Process / Operations / Reference / Archive / ADR); registered `CHANGELOG.md`
- Not done (deliberate):
  the operational docs (`runtime.md`, `operations.md`, `runbook.md`, `STORAGE.md`, `alerts.md`, `docker-compose.md`, `worker-scaling-roadmap.md`) were grouped in the index but NOT moved into an `operations/` subfolder — that would break ~24 inbound links including references inside historical `agents/tasks/TASK-*.md`, for marginal gain over index grouping

## Resolved Items

### DOC-007 No explicit documentation ownership model

- Status: `resolved`
- Fix:
  created `Documentation/documentarian-role.md` and synchronized mirror

### DOC-008 No documentation map

- Status: `resolved`
- Fix:
  created `Documentation/doc-index.md` and synchronized mirror

### DOC-009 No dedicated post-release architecture roadmap

- Status: `resolved`
- Fix:
  created `Documentation/architecture-roadmap.md` and synchronized mirror

### DOC-010 No durable decision record mechanism

- Status: `resolved`
- Fix:
  created ADR directory, template, index, and initial ADRs

---

## Update Rule

Update this file when:

- a document is discovered to be stale or misleading
- a structural documentation issue is fixed
- a formerly active document becomes historical
- a source-of-truth conflict is discovered
