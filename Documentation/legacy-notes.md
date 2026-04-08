# Legacy Notes Register

## Purpose

This document tracks repository notes that still exist for historical value but should not be treated as active project guidance.

It exists to reduce confusion without immediately deleting older material.

---

## Usage Rule

If a document appears here, it is not an active source of truth unless it is explicitly promoted later.

Use these notes for:

- historical context
- earlier product framing
- old planning assumptions
- external inspiration/reference

Do not use them for:

- current task status
- current architecture direction
- current runtime behavior
- release readiness decisions

---

## Current Legacy Notes

### `Documentation/Main idea.md`

- Classification: `reference / historical`
- Why it exists:
  earlier product framing and concept description
- Current caution:
  terminal rendering shows encoding problems, so treat it as unstable for direct maintenance until a dedicated cleanup pass is done

### `Documentation/MVP Plan.md`

- Classification: `historical`
- Why it exists:
  earlier planning context
- Current caution:
  should not be read as the current roadmap or current delivery plan, and should be edited only after a safe encoding-normalization pass

### `Documentation/DESIGN.md`

- Classification: `reference`
- Why it exists:
  external design-system reference material
- Current caution:
  not the active StreamCut design or architecture source

### `Documentation/HELP.md`

- Classification: `historical / generated`
- Why it exists:
  generated scaffold content from project bootstrap
- Current caution:
  not useful as active project documentation

---

## Cleanup Candidates

These documents are candidates for later cleanup, rewrite, archival, or relocation:

- `Documentation/Main idea.md`
- `Documentation/MVP Plan.md`
- `Documentation/DESIGN.md`
- `Documentation/HELP.md`

---

## Preferred Active Replacements

When current truth is needed, prefer:

- `Documentation/backlog.md`
- `Documentation/architecture-roadmap.md`
- `Documentation/runtime.md`
- `Documentation/operations.md`
- `Documentation/release-checklist.md`
- `Documentation/doc-index.md`

---

## Next Cleanup Actions

- normalize encoding where practical
- add explicit status headers to legacy files when safe
- move clearly obsolete material into `Documentation/archive/` if the project wants a cleaner active docs surface
