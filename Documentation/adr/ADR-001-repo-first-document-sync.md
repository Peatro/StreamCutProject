# ADR-001 Repository-First Documentation Sync

## Status

Accepted

## Date

2026-04-08

## Context

The project now maintains documentation both inside the repository and inside an Obsidian vault.

Without an explicit sync rule, this creates predictable failure modes:

- conflicting backlog status
- duplicated roadmap text
- stale notes that look authoritative
- uncertainty about where updates should be made first

The project needs a durable rule that keeps documentation trustworthy while still allowing Obsidian to be useful for planning and note-taking.

## Decision

Repository documentation is the canonical source of truth.

Obsidian notes are synchronized mirrors unless a specific document explicitly says otherwise.

The default update order is:

1. update repository document
2. update Obsidian mirror

## Alternatives Considered

### Obsidian-first documentation workflow

- attractive for fast note-taking
- rejected because code-adjacent project state must stay authoritative in the repository

### Dual-authoritative documentation

- both repo and Obsidian treated as equal sources of truth
- rejected because it creates inevitable drift and conflict over time

## Consequences

### Positive

- documentation authority is explicit
- backlog and roadmap synchronization becomes operationally manageable
- docs can be reviewed and versioned alongside code

### Negative

- every mirrored change requires an explicit sync step
- spontaneous Obsidian edits are no longer safe unless mirrored back properly

### Follow-Up

- keep source-of-truth notes explicit in mirrored documents
- maintain `Documentation/doc-index.md`
- maintain synchronized backlog status between repo and Obsidian

## Related Documents

- `Documentation/documentarian-role.md`
- `Documentation/doc-index.md`
- `Documentation/backlog.md`

## Notes

This ADR is intentionally procedural. The goal is to prevent documentation drift before the project accumulates too many parallel notes.
