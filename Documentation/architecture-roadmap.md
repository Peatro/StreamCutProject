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

What is still missing:

- retry/backoff/dead-letter task semantics
- explicit transition/orchestration layer
- dedicated `export-worker`
- signed-URL-first media delivery
- durable object-storage-first execution contract
- quotas and fairness controls
- queue delivery seam for later broker migration

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

### Phase A. Finish `v1.0.0`

Goal:

- release a stable operator-facing service for a small authenticated team

Focus:

- diagnostics
- recovery clarity
- operator controls
- observability
- cleanup
- release discipline

Tracked primarily in:

- `Documentation/backlog.md`

### Phase B. Make Task Lifecycle Explicit

Goal:

- turn the current persisted task layer into a fully explicit execution model

Primary tasks:

- `TASK-068`
- `TASK-069`

Outcomes:

- retry budgets
- delayed retry/backoff
- dead-letter state
- explicit transition rules
- clearer split between `VodJob` and `worker_task`

### Phase C. Split Worker Responsibilities Cleanly

Goal:

- align runtime topology with workload boundaries

Primary task:

- `TASK-070`

Outcomes:

- `download-worker`
- `analyze` or `processing-worker`
- `export-worker`

### Phase D. Normalize Delivery And Storage Contracts

Goal:

- stop using backend as the default media proxy
- make durable storage references the contract where needed

Primary tasks:

- `TASK-071`
- `TASK-072`

Outcomes:

- signed URLs for completed exports
- reduced backend media streaming
- durable artifact references
- local disk treated as scratch space

### Phase E. Add Guardrails And Economics

Goal:

- prevent abuse and unrealistic workload shapes before growth

Primary task:

- `TASK-073`

Outcomes:

- max concurrent jobs
- max duration or equivalent input guards
- export limits
- explicit failure responses for exceeded limits

### Phase F. Prepare The Queue Boundary

Goal:

- create a clean future seam for queue delivery migration

Primary task:

- `TASK-074`

Outcomes:

- durable task state remains in backend persistence
- delivery mechanism can later move beyond direct DB polling
- future broker adoption stays evidence-based

---

## Recommended Order

1. `TASK-068`
2. `TASK-069`
3. `TASK-070`
4. `TASK-071`
5. `TASK-072`
6. `TASK-073`
7. `TASK-074`

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
