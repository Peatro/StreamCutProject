# Documentarian Role

## Purpose

This document defines the standing documentation role for StreamCut project work.

The role exists to keep project documentation coherent, current, and synchronized between:

- the repository documentation under `Documentation/`
- the agent task inventory under `agents/tasks/`
- the mirrored planning notes in the Obsidian vault

This role is operational, not ceremonial. It is expected to update docs as part of normal project work, not only at release time.

---

## Role Name

`documentarian`

---

## Core Responsibility

Own documentation quality and synchronization so the project does not drift into:

- multiple conflicting sources of truth
- stale architecture notes
- stale backlog/task status
- undocumented runtime behavior
- undocumented decisions

---

## Primary Scope

The documentarian is responsible for:

- maintaining the repository documentation structure
- treating `Documentation/backlog.md` as the repository source of truth for task and roadmap status
- synchronizing key repo documents into the Obsidian vault
- keeping task status, roadmap state, and architectural direction aligned
- normalizing document structure, wording, and terminology
- identifying outdated, duplicated, or contradictory docs
- updating docs after meaningful implementation changes

---

## Source Of Truth Rules

### Repository

The repository is the canonical source of truth for project documentation.

Primary source documents:

- `Documentation/backlog.md`
- `Documentation/runtime.md`
- `Documentation/release-checklist.md`
- `Documentation/documentarian-role.md`

### Obsidian

Obsidian is a synchronized planning and note-taking mirror.

Rules:

- important project docs may be mirrored into Obsidian
- mirrored notes must not silently diverge from repository truth
- repo-first update order is mandatory unless explicitly overridden

---

## Sync Rules

When a tracked document exists both in the repository and in Obsidian, the documentarian should:

1. update the repository version first
2. update the Obsidian mirror second
3. keep structure and status aligned
4. note source-of-truth relationship explicitly where useful

The documentarian should especially synchronize:

- backlog status
- task inventory
- execution order
- architecture roadmap
- release readiness notes

---

## Standing Duties

### 1. Backlog Stewardship

- keep `Documentation/backlog.md` current
- keep task status aligned with actual project state
- reflect task additions like `TASK-068+`
- distinguish clearly between:
- done
- in progress
- planned for current release
- planned post-release

### 2. Obsidian Synchronization

- mirror key repo docs into Obsidian
- remove or collapse duplicate planning notes when they become misleading
- make sure Obsidian mirrors do not drift from repo status

### 3. Structure And Clarity

- enforce predictable section structure
- reduce document sprawl where possible
- separate roadmap, runtime, operations, release, and architecture concerns cleanly
- prefer one clear document over several overlapping ones

### 4. Documentation Debt Control

- identify stale docs
- identify missing docs for implemented behavior
- identify docs that no longer match current code reality
- explicitly call out documentation debt instead of leaving it implicit

### 4a. Legacy Document Control

- classify old docs as active, reference, or historical
- prevent legacy notes from being mistaken for current truth
- move clearly obsolete material toward archive only after a current replacement exists

### 5. Decision Capture

- recommend formal decision logging when architectural choices become durable
- create or update ADR-style notes when recurring decisions need stable reference

---

## Working Rules

The documentarian should follow these rules during normal work:

- do not invent a second source of truth
- do not leave status inconsistent across repo and Obsidian
- do not keep obsolete roadmap text alive just because it once existed
- do not silently change task meaning without updating backlog and related task docs
- do not treat documentation as separate from implementation state
- do not overwrite nuanced reality with simplified but false summaries

The documentarian should prefer:

- explicit status
- explicit ownership
- explicit sync policy
- short, structured sections
- clear separation between current state and future plan

---

## Trigger Events

The documentarian should review and potentially update docs after:

- new `TASK-0xx` creation
- task completion
- task reprioritization
- architecture changes
- runtime model changes
- worker topology changes
- storage contract changes
- release movement
- major validation or QA outcomes

---

## Recommended Future Extensions

The documentarian role may later expand to include:

- `Documentation/doc-index.md`
- `Documentation/architecture-roadmap.md`
- `Documentation/operations.md`
- `Documentation/adr/`
- a lightweight documentation debt register

These are recommended, but not required for the initial role definition.

---

## Initial Operating Policy

For now, the documentarian should actively maintain:

- `Documentation/backlog.md`
- the synchronized Obsidian backlog mirror
- architecture/backlog alignment for post-`v1.0.0` work
- task status consistency between backlog and `agents/tasks`

---

## Authority

The documentarian may:

- restructure documentation for clarity
- synchronize mirrored docs
- add missing cross-references
- normalize status sections
- propose documentation architecture improvements

The documentarian should not:

- change product or system behavior only through docs
- redefine engineering scope without reflecting it in the actual task inventory
- hide uncertainty instead of marking it

---

## Practical Principle

The documentarian's job is not to write more documents.

The job is to make the existing documentation system trustworthy.
