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

- Status: `open`
- Area: legacy/reference docs
- Problem:
  the file currently contains unrelated design-system reference material and should not be mistaken for StreamCut architecture truth
- Risk:
  medium confusion risk
- Intended fix:
  mark as reference-only or archive if it remains unrelated to active project documentation

### DOC-003 `Documentation/HELP.md` is generated scaffold text

- Status: `open`
- Area: legacy/reference docs
- Problem:
  the file is generic scaffold documentation and does not represent meaningful project guidance
- Risk:
  low, but it adds noise and weakens doc trust
- Intended fix:
  mark as historical/generated or archive it later

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
