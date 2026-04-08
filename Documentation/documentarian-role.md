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
- maintaining repository-canonical agent operating documents under `agents/`
- treating `Documentation/backlog.md` as the repository source of truth for task and roadmap status
- synchronizing key repo documents into the Obsidian vault
- keeping task status, roadmap state, and architectural direction aligned
- normalizing document structure, wording, and terminology
- identifying outdated, duplicated, or contradictory docs
- updating docs after meaningful implementation changes
- keeping agent operating contracts aligned with the actual execution model

---

## Source Of Truth Rules

### Repository

The repository is the canonical source of truth for project documentation.

Primary source documents:

- `Documentation/backlog.md`
- `Documentation/runtime.md`
- `Documentation/release-checklist.md`
- `Documentation/documentarian-role.md`
- `agents/contracts/*.md`
- `agents/roles/*.md`
- `agents/global/*.md`
- `agents/templates/*.md`

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

Agent operating docs under `agents/` are repository-first by default.
They should not gain an independent full Obsidian mirror unless there is an explicit maintenance reason to do so.

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
- keep agent operating docs separated into stable rules, current architecture truth, and role or template files

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

## Recurring Maintenance Responsibilities

The documentarian is expected to perform ongoing maintenance, not one-time setup only.

Recurring responsibilities include:

- keep `Documentation/backlog.md` aligned with actual task state
- keep the Obsidian backlog mirror synchronized
- keep `Documentation/doc-index.md` aligned with the current document set
- keep the Obsidian task archive synchronized with repository task files and backlog status
- update `Documentation/documentation-debt.md` when documentation gaps are discovered or resolved
- update `Documentation/legacy-notes.md` when older documents change classification
- add or update ADRs when architecture decisions become durable
- keep `runtime`, `operations`, `storage`, and `release-checklist` cross-referenced and non-contradictory
- keep `agents/contracts`, `agents/roles`, `agents/global`, and `agents/templates` aligned with current architecture direction

---

## Maintenance Checklists

### After Task Status Change

When a `TASK-0xx` changes state:

1. update `Documentation/backlog.md`
2. update the Obsidian backlog mirror
3. confirm any related `agents/tasks/TASK-0xx.md` references still match backlog wording and sequencing
4. update roadmap or ADR references if the task changed architecture direction

### After New Task Creation

When a new `TASK-0xx` is added:

1. add it to the repository backlog
2. add it to the synchronized Obsidian backlog mirror
3. place it in the correct release or post-release track
4. update execution order or dependency notes if needed
5. update architecture roadmap if the task changes long-term direction

### After Architecture Change

When architecture direction changes:

1. update `Documentation/architecture-roadmap.md`
2. update `Documentation/backlog.md` if sequencing or ownership changes
3. decide whether a new ADR is required
4. sync relevant Obsidian mirrors
5. update `Documentation/doc-index.md` if document ownership or structure changed
6. update agent operating docs under `agents/` if the execution model, contracts, or role boundaries changed

### After Runtime Or Storage Change

When runtime, worker topology, or storage contracts change:

1. update `Documentation/runtime.md`
2. update `Documentation/STORAGE.md` if storage behavior changed
3. update `Documentation/operations.md` if operator behavior changed
4. update backlog notes if the change affects task status or roadmap ordering
5. sync any mirrored Obsidian docs

### After Release-Process Change

When release gates, release flow, or rollout assumptions change:

1. update `Documentation/release-checklist.md`
2. update `Documentation/backlog.md`
3. update `Documentation/operations.md` if operator procedure changed
4. sync affected Obsidian mirrors

### Repo To Obsidian Sync Checklist

When syncing a mirrored document:

1. confirm repository version is already correct
2. update the Obsidian mirror second
3. preserve source-of-truth wording
4. avoid adding mirror-only status that contradicts repository status
5. update sync metadata if the mirror carries it

---

## Task Archive Maintenance

The Obsidian task archive is a navigation mirror for repository task files.

Source of truth:

- task definitions: `agents/tasks/`
- task statuses: `Documentation/backlog.md`

The documentarian should maintain:

- Obsidian `Архив задач/TASK Archive Index.md`
- Obsidian `Архив задач/TASK Archive Table.md`
- status-group task mirrors under:
- `01 Completed`
- `02 In Progress`
- `03 Planned v1.0.0`
- `04 Planned post-v1.0.0`

### Task Archive Update Rules

When a task status changes:

1. update repository backlog first
2. confirm repository `agents/tasks/TASK-xxx.md` is still the correct source definition
3. move the mirrored task file into the correct archive group
4. refresh archive header metadata in the mirrored task file
5. refresh `TASK Archive Index.md`
6. refresh `TASK Archive Table.md`

When a new task file is created:

1. create the repository task file first
2. add it to the repository backlog
3. mirror it into the correct Obsidian archive group
4. update archive index and table

The task archive must not become an independent status-tracking system.

It is a mirrored navigation layer only.

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
- modernize agent operating docs when they lag behind the actual execution model

The documentarian should not:

- change product or system behavior only through docs
- redefine engineering scope without reflecting it in the actual task inventory
- hide uncertainty instead of marking it

---

## Practical Principle

The documentarian's job is not to write more documents.

The job is to make the existing documentation system trustworthy.
