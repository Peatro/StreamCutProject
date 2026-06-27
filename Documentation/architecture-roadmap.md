# Architecture Roadmap

## Purpose

This document defines the architecture path after the current `v1.0.0` service-hardening track.

It is intentionally separate from `Documentation/backlog.md`:

- `backlog.md` tracks project status and task inventory
- this file tracks architecture direction, sequencing, and rationale

---

## Current Position

The project is not starting from zero.

What already exists:

- Spring Boot backend
- PostgreSQL persistence
- `worker_task` and `worker_execution`
- heartbeat-based stale recovery
- `download-worker` and `processing-worker`
- MinIO / S3-compatible export artifact storage

What was added through `TASK-072`:

- retry/backoff/dead-letter task semantics
- explicit transition/orchestration layer (`TaskTransitionService`)
- dedicated `export-worker`
- signed-URL-first media delivery
- durable object-storage-first execution contract

What was added through the product-quality track (`TASK-076` through `TASK-080`):

- audio loudness signal in clip candidate scoring
- download stall detection via progress-gated heartbeat
- job auto-complete on moderation and manual complete endpoint
- delete allowed from `READY_FOR_REVIEW`
- clip export audio desync fix and CPU bound fix
- job UI delete, bulk clear, complete button, static progress bar

What is still missing (deprioritized for single-operator use):

- quotas and fairness controls (`TASK-073`, revisit when going public)
- queue delivery seam for later broker migration (`TASK-074`, evidence-gated)
- React UI migration (`TASK-075`, vanilla JS sufficient for operator self-use)

---

## Roadmap Principle

Do not jump to infrastructure complexity before execution semantics are correct.

This means:

- no early Kubernetes move
- no early RabbitMQ move
- no fake scale architecture built on ambiguous task semantics

First shape the execution model. Then scale it.

---

## Architecture Phases

### Phase A. Finish `v1.0.0` (completed)

Goal:

- release a stable operator-facing service for a small authenticated team

Status: completed through `TASK-066` on 2026-04-09

### Phase B. Make Task Lifecycle Explicit (completed)

Goal:

- turn the current persisted task layer into a fully explicit execution model

Primary tasks:

- `TASK-068`
- `TASK-069`

Status: completed / merged into `develop`

### Phase C. Split Worker Responsibilities Cleanly (completed)

Goal:

- align runtime topology with workload boundaries

Primary task:

- `TASK-070`

Status: completed / merged into `develop`

### Phase D. Normalize Delivery And Storage Contracts (completed)

Goal:

- stop using backend as the default media proxy
- make durable storage references the contract where needed

Primary tasks:

- `TASK-071`
- `TASK-072`

Status: completed / merged into `develop`

### Phase D+. Product Quality Track (completed)

Goal:

- make the clip-selection tool fully usable for the operator's own Twitch VODs before pursuing multi-tenant/scale architecture

Primary tasks:

- `TASK-076` audio loudness signal in clip scoring
- `TASK-077` download stall detection via progress-gated heartbeat
- `TASK-078` job UI: delete, bulk clear, complete button, static progress bar
- `TASK-079` job auto-complete on moderation, manual complete, delete from `READY_FOR_REVIEW`
- `TASK-080` clip export audio desync fix and CPU bound fix

Rationale:

- post-`v1.0.0` priority shifted to operator-facing clip-quality and usability driven by real-use feedback before pursuing quotas, broker abstraction, or React migration
- the current tool is single-operator/self-use; multi-tenant and scale work waits until going public

Status: completed / merged into `develop`

### Phase E. Add Guardrails And Economics (deprioritized)

Goal:

- prevent abuse and unrealistic workload shapes before growth

Primary task:

- `TASK-073`

Outcomes:

- max concurrent jobs
- max duration or equivalent input guards
- export limits
- explicit failure responses for exceeded limits

Status: deprioritized. Not justified for single-operator/self use. Revisit when going public.

### Phase F. Prepare The Queue Boundary (deprioritized)

Goal:

- create a clean future seam for queue delivery migration

Primary task:

- `TASK-074`

Outcomes:

- durable task state remains in backend persistence
- delivery mechanism can later move beyond direct DB polling
- future broker adoption stays evidence-based

Status: deprioritized. DB polling is fine at n=1. Evidence-gated.

---

## Recommended Order

1. `TASK-068` (completed)
2. `TASK-069` (completed)
3. `TASK-070` (completed)
4. `TASK-071` (completed)
5. `TASK-072` (completed)
6. `TASK-076` through `TASK-080` product-quality track (completed)
7. `TASK-073` (deprioritized)
8. `TASK-074` (deprioritized)

---

## Why This Order

### First: execution semantics

Without explicit retries, delayed availability, and transition rules, every later architecture layer is built on ambiguous behavior.

### Second: worker topology

Once execution state is explicit, worker pools can be separated cleanly.

### Third: storage and delivery

Once worker ownership is clean, media delivery and durable storage contracts can be simplified without hidden coupling.

### Fourth: quotas

Limits and fairness only become trustworthy after execution ownership and concurrency are explicit.

### Fifth: broker seam

A queue broker should replace delivery mechanics, not become the first place where execution semantics are invented.

---

## What This Roadmap Explicitly Avoids

- architecture-for-architecture's-sake
- early Kubernetes adoption
- broker-first design before task semantics are stable
- keeping backend as the long-term media transport layer
- treating local host paths as durable system truth

---

## Likely ADR Topics

The following roadmap steps should probably produce ADRs:

- task-centric execution model
- transition layer extracted from `VodJobService`
- export-worker separation
- signed URL delivery policy
- object storage as durable artifact contract
- queue delivery abstraction boundaries

---

## Relation To Other Documents

- `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md`
  authoritative architecture of the highlight-detection / clip-selection / scoring / fusion pipeline.
  This roadmap covers execution semantics, worker topology, storage, and delivery; the brief owns the
  highlight pipeline (detection vs judgment layers, the LLM's role, arithmetic fusion, epistemic status).

- `Documentation/backlog.md`
  status, task inventory, release progress

- `Documentation/worker-scaling-roadmap.md`
  runtime scaling guidance and worker deployment evolution

- `Documentation/STORAGE.md`
  storage-specific behavior and operator assumptions

- `Documentation/adr/`
  durable decision records created from roadmap milestones

---

## Update Rule

Update this document when:

- the post-`v1.0.0` sequence changes
- an architecture phase is completed
- a major dependency changes
- a new durable decision supersedes the current path

---

## Existing ADR References

This roadmap already depends on:

- `Documentation/adr/ADR-001-repo-first-document-sync.md`
- `Documentation/adr/ADR-002-task-centric-execution-model.md`
