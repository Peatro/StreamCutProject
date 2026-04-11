# Worker Scaling Roadmap

## Purpose
This document records a pragmatic transition path from the current local Docker Compose worker runtime to a scalable worker architecture, and describes the target architecture in the terms already used in this project.

The intent is to avoid premature Kubernetes adoption while still designing the system so that Kubernetes can become a clean later step instead of a rewrite.

## Current State Summary
Today the system is effectively:

- Spring Boot backend as the orchestration source of truth
- `download-worker` and `processing-worker` running as separate Docker Compose services
- worker progress reported back to backend through `/api/internal/worker/**`
- shared local storage volume for media and intermediate artifacts
- processing model cache stored outside image build and reused at runtime
- worker polling used to claim the next job
- persisted `worker_task` and `worker_execution` records in PostgreSQL
- stale task recovery driven from persisted heartbeat state
- `VodJob` increasingly treated as a projection over task state rather than the only orchestration source
- task and execution history exposed through the job APIs

This is acceptable for MVP and local operation, and the codebase has already moved partway into the task-centric transition. It is still not yet the right shape for robust horizontal scaling or Kubernetes-based worker orchestration.

## Current Position
Relative to the original plan, the project is currently:

- beyond the original "split workers" milestone
- at the end of the "separate worker lifecycle from Compose" step in source
- not yet at the "real queue" step

Concretely, the following are already implemented:

- explicit `worker_task` persistence for `DOWNLOAD`, `ANALYZE`, and `EXPORT`
- explicit `worker_execution` persistence tied to task ownership
- task-centric claim flow
- stale recovery based on task heartbeat
- task and execution history APIs
- latest task and execution summaries in the UI
- read-side and write-side `VodJob` recompute/projection from task state

The following are still missing and remain the next real architecture step:

- retry counts and retry budgets
- backoff and `available_at` semantics
- dead-letter semantics
- dedicated transition/orchestration service separate from `VodJobService`
- broker-backed queue delivery
- dedicated `export-worker`

## Recommended Order

### 1. Stabilize the Current Compose Runtime
Goal: make the current single-host runtime predictable, observable, and operationally boring.

Required outcomes:

- `backend`, `download-worker`, and `processing-worker` start reliably
- `processing-worker` model cache is decoupled from image build
- job progress is visible in API and UI
- worker heartbeat freshness and stale behavior are visible
- cancel and restart semantics are safe through `processingVersion` and worker lease validation
- failures are captured in backend state and event history

Recommended additions on this step:

- add health and readiness semantics for backend and workers
- define timeout and retry policy for claim and callback transport
- ensure logs are structured around `jobId`, `workerId`, and stage
- expose minimal metrics for backlog, active jobs, failures, and stalled jobs
- add container resource limits even if still running only on Docker

Current status:

- mostly implemented in source
- remaining work is operational hardening rather than first-time plumbing
- the biggest open validation items are long-running processing behavior and operator-facing diagnostics

Why this comes first:

- instability on one host does not get fixed by moving to Kubernetes
- the pipeline logic must be correct before the deployment model becomes more complex

### 2. Separate Worker Lifecycle from Compose
Goal: stop treating Docker Compose itself as the worker orchestration layer.

What changes conceptually:

- workers become execution agents
- backend becomes the owner of task lifecycle rules
- task execution becomes explicit instead of implicit inside worker polling behavior

Required outcomes:

- clearer task model for `DOWNLOAD`, `ANALYZE`, and `EXPORT`
- explicit execution ownership and lease handling
- retries attached to tasks, not only to whole jobs
- idempotent result and failure ingestion
- clearer distinction between job state and task state

Current status:

- largely implemented in source
- `worker_task` and `worker_execution` now exist and already carry most of the ownership model
- the main missing piece is not task persistence itself, but richer task lifecycle semantics and transition extraction

Why this matters:

- Compose should only keep services running
- it should not be the main mechanism that defines how work is assigned, retried, or recovered

### 3. Introduce a Real Queue
Goal: replace worker polling as the primary work-distribution mechanism.

This is the most important architecture step before Kubernetes.

Reasoning:

- Kubernetes manages containers
- a queue manages work
- scaling workers without a proper queue only scales process count, not execution discipline

Candidate queue options:

1. PostgreSQL-backed queue as the simplest first operational step
2. Redis-based queue if higher throughput or simpler worker libraries are needed
3. RabbitMQ if routing, delivery semantics, and acknowledgements become more important
4. cloud-managed queue if the platform later moves in that direction

For this project, the recommended order is:

1. start with a PostgreSQL queue if the goal is minimum infrastructure change
2. move later to Redis or RabbitMQ only if the queue becomes a real bottleneck or routing needs grow

Required outcomes:

- backend creates tasks instead of waiting for workers to poll arbitrary job states
- workers reserve tasks from a queue
- tasks have retry and visibility semantics
- stuck worker execution can be recovered without ad hoc logic
- `DOWNLOAD`, `ANALYZE`, and `EXPORT` can scale independently

Current status:

- the codebase has already implemented the task side of this transition
- what is still missing is queue delivery semantics proper
- the next meaningful step is to enrich the current PostgreSQL-backed task model with retry/backoff/dead-letter behavior before introducing a separate broker

### 4. Make Workers Truly Stateless
Goal: remove hidden assumptions that work only because everything currently lives on one host.

What must change:

- workers should not depend on local host-specific paths being preserved forever
- source video, audio, intermediate outputs, and export outputs should have stable external references
- any worker should be able to resume work on another machine
- worker runtime should be replaceable without losing the ability to process pending tasks

Required outcomes:

- storage references become durable identifiers rather than host-bound path assumptions
- local disk becomes a cache or transient scratch space, not the long-term contract
- export artifacts and input/source artifacts live in shared or external storage
- intermediate worker outputs can be recreated or fetched without binding execution to one node

Why this matters:

- Kubernetes assumes that pods are disposable
- a pipeline that depends on one machine's local filesystem is fragile even before Kubernetes

### 5. Split Worker Pools by Responsibility
Goal: scale and tune each workload independently.

Recommended worker pools:

- `download-worker`
- `processing-worker-cpu`
- later optionally `processing-worker-gpu`
- `export-worker`

Why this split matters:

- downloading media is operationally different from transcription and scoring
- export workloads have different CPU and I/O characteristics
- processing may later need different hardware classes
- scaling only the bottleneck pool is cheaper and clearer

Required outcomes:

- task routing by worker capability
- separate concurrency controls per worker type
- separate throughput and latency metrics per worker pool
- support for future CPU and GPU specialization

Current source note:

- the checked-in CPU-first runtime remains the default path
- `docker-compose.gpu.yml` now provides an optional local GPU override for `processing-worker`
- `worker_execution.whisper_device` now records the processing worker's reported whisper device for operator visibility

### 6. Add Real Observability
Goal: understand system behavior numerically instead of inferring everything from logs.

Minimum recommended metrics:

- queue depth by task type
- queue age by task type
- number of active workers by role
- job throughput
- task throughput
- task failure rate
- retry count
- stalled task count
- average stage duration
- processing worker startup time
- model load time
- export duration

Recommended tooling:

- Prometheus-style metrics
- dashboards for queue depth, failures, and active workers
- alerts for backlog growth, repeated failures, and stalled execution
- log correlation by `jobId`, `taskId`, and `workerId`

Why this comes before Kubernetes:

- moving a blind system into a cluster just makes debugging harder
- autoscaling without trustworthy metrics is mostly guesswork

### 7. Adopt Kubernetes Only After the Above Is True
Goal: use Kubernetes as an orchestrator for a system that is already shaped correctly.

At this point Kubernetes starts providing real value:

- separate scaling of `processing-worker`
- pod self-healing
- rolling deploys
- resource isolation
- CPU and GPU node pool separation
- autoscaling from queue depth through HPA or KEDA

Kubernetes should not be the first answer. It should be the deployment step taken after queue semantics, storage semantics, and observability are already correct.

## What Should Not Be Done Yet

- do not move to Kubernetes while task distribution is still primarily worker polling
- do not rely on one shared local host volume as the durable system contract
- do not invest in Helm charts and cluster automation before worker execution semantics are stable
- do not treat container orchestration as a substitute for queue design

## Readiness Check for Kubernetes
The system is ready for Kubernetes when these questions already have clear answers:

- How is work queued?
- How is work retried?
- How is a stuck task detected?
- How is ownership of a running task represented?
- How can a task resume on another worker or another node?
- Where do artifacts live?
- How do worker types receive different task classes?
- What metrics determine scaling and alerting?

If those answers still depend mainly on Docker Compose and a shared local volume, the system is not yet ready.

## Target Architecture

### High-Level Shape
The target system should look like this:

1. `backend` owns durable job and task state.
2. `queue` owns delivery and reservation of executable tasks.
3. `worker pools` execute tasks by capability.
4. `artifact storage` owns durable media and export artifacts.
5. `metrics and logs` provide visibility into throughput, failures, and stalls.

### Domain Model Alignment
The current project already has useful domain concepts:

- `VodJob`
- `JobStatus`
- `WorkerProgressUpdatePayload`
- `WorkerProcessingResultPayload`
- `WorkerDownloadResultPayload`
- `WorkerExportResultPayload`
- `WorkerFailureReportPayload`
- job event history

These should remain useful, but the execution model should become more explicit through a task layer.

### Recommended Core Entities

#### `vod_job`
Keep `VodJob` as the operator-facing aggregate.

Suggested responsibility:

- represents the end-to-end user-visible job
- owns source information and operator-visible lifecycle state
- owns summary progress and latest worker-facing message
- remains the primary entity shown in UI

Suggested statuses:

- `NEW`
- `QUEUED_FOR_DOWNLOAD`
- `DOWNLOADING`
- `QUEUED_FOR_PROCESSING`
- `EXTRACTING_AUDIO`
- `TRANSCRIBING`
- `DETECTING_SILENCE`
- `ANALYZING_WINDOWS`
- `GENERATING_CANDIDATES`
- `READY_FOR_REVIEW`
- `EXPORTING_CLIP`
- `COMPLETED`
- `FAILED`
- `CANCELED`

This keeps the current UI and operator model understandable.

#### `worker_task`
Add a dedicated task table or equivalent queue-backed representation.

Suggested fields:

- `id`
- `job_id`
- `task_type`
- `task_status`
- `attempt`
- `priority`
- `lease_owner`
- `lease_until`
- `created_at`
- `updated_at`
- `started_at`
- `finished_at`
- `payload_json`
- `result_json`
- `error_message`

Suggested `task_type` values:

- `DOWNLOAD`
- `ANALYZE`
- `EXPORT`

Suggested `task_status` values:

- `QUEUED`
- `LEASED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `DEAD_LETTER`
- `CANCELED`

Why this matters:

- `VodJob` remains the product concept
- `worker_task` becomes the execution concept

### Queue Responsibilities
The queue layer should:

- deliver available tasks to eligible worker pools
- reserve work for a limited time
- return expired tasks for retry or reassignment
- support task retries and dead-letter behavior
- expose queue depth and queue age as first-class metrics

The queue can be implemented first with PostgreSQL semantics, but the contract should already look like a real task queue.

### Worker Pools

#### Download Worker Pool
Responsibility:

- materialize source video from URL or confirm uploaded file presence
- persist source artifact location
- enqueue the next `ANALYZE` task

Should not:

- load Whisper model
- perform analysis or export work

#### Processing Worker Pool
Responsibility:

- extract audio
- transcribe
- detect silence
- analyze windows
- generate candidates
- persist processing outputs

Implementation guidance:

- this pool owns the transcription model cache
- tasks should be stateless aside from cache and temporary scratch space
- progress updates should include intermediate progress inside long stages like `TRANSCRIBING`

Future extension:

- split further into CPU and GPU processing pools if model throughput demands it

#### Export Worker Pool
Responsibility:

- export approved candidate clips
- persist final artifact
- report export completion back into the job aggregate

### Storage Architecture

#### Durable Artifact Storage
Durable storage should hold:

- source video
- extracted audio if it must survive retries or downstream steps
- export artifacts
- optionally transcript or analysis artifacts if externalized later

Long-term contract should be based on stable references, not only on host-local paths.

#### Local Scratch Space
Workers may still use local disk for:

- temporary transcoding files
- temporary ffmpeg outputs
- temporary processing caches

But scratch space should not be the durable contract between backend and workers.

#### Model Cache
The transcription model cache should:

- belong only to processing workers
- persist outside the image build
- remain a runtime optimization rather than part of immutable build artifacts

### Progress and State Flow
Recommended flow for a typical URL-based job:

1. Operator creates `VodJob`.
2. Backend creates a `DOWNLOAD` task.
3. Download worker leases the task and sets job state to `DOWNLOADING`.
4. Download worker stores the source artifact and reports success.
5. Backend marks the download task succeeded and enqueues an `ANALYZE` task.
6. Processing worker leases the task and begins analysis stages.
7. Backend updates `VodJob.status`, `progressPercent`, `progressMessage`, and heartbeat as progress arrives.
8. Processing worker reports transcript, silence spans, analysis windows, and clip candidates.
9. Backend marks the task succeeded and moves `VodJob` to `READY_FOR_REVIEW`.
10. Operator requests export for an approved candidate.
11. Backend creates an `EXPORT` task.
12. Export worker leases the task and exports the clip.
13. Backend stores the final artifact reference and marks the job `COMPLETED`.

### Retry and Failure Model
Recommended behavior:

- task failures should not immediately destroy whole-job observability
- failed tasks should record attempt count, last error, and failed stage
- retry policy should differ by task type
- dead-letter tasks should remain inspectable by operators
- terminal job failure should be explicit after retry policy is exhausted or operator cancellation occurs

Example:

- `DOWNLOAD` may retry network failures
- `ANALYZE` may retry transient infrastructure failures but not necessarily model/input corruption
- `EXPORT` may retry artifact persistence failures

### Stalled Work Detection
The target architecture should define stalled work explicitly.

Suggested rules:

- a running task must refresh heartbeat within a known interval
- heartbeat thresholds may differ by task type or stage
- `TRANSCRIBING` may tolerate a larger window than short stages
- expired leases should trigger recovery logic
- stalled tasks should produce metrics and alerts

This should be a task concept first, and only then reflected into `VodJob` summary state.

### Observability Model
Required labels and identifiers:

- `jobId`
- `taskId`
- `workerId`
- `taskType`
- `stage`
- `attempt`

Recommended dashboards:

- queue depth by task type
- queue age by task type
- running tasks by worker role
- failure rate by task type
- stale task count
- average transcription duration
- export latency
- model load time for processing workers

Recommended alerts:

- queue backlog exceeds threshold
- heartbeat missing beyond threshold
- repeated task failures beyond threshold
- no active processing workers while analyze tasks are queued

### Transition Path from the Current Codebase

#### Step A
Keep the current API and `VodJob` lifecycle intact, but introduce an internal task abstraction.

Status:

- completed in source

#### Step B
Make worker claim logic task-centric instead of job-status-centric.

Status:

- completed in source

#### Step C
Introduce queue-backed leasing semantics while preserving current result payload contracts where possible.

Status:

- partially completed in source through persisted task/execution ownership and stale recovery
- still missing retry budgets, backoff, and dead-letter semantics

#### Step D
Move artifact references toward durable storage identifiers instead of raw local-path coupling.

Status:

- partially completed
- export artifacts already use object storage, but the broader worker contract is still not fully durable-storage-first

#### Step E
Split scaling and metrics by worker pool.

Status:

- partially completed
- worker roles are split, but dedicated export pool and metrics-driven scaling are still future work

#### Step F
Only after the above is stable, deploy worker pools under Kubernetes if the runtime scale justifies it.

Status:

- not started, and still intentionally not the next step

## Final Recommendation
The next major investment should not be Kubernetes first.

The next major investment should be:

1. complete retry/backoff/dead-letter semantics on top of the existing task model
2. extract transition/orchestration rules further away from `VodJobService`
3. continue storage decoupling toward durable references
4. improve observability and operator controls
5. only then introduce stronger queue delivery semantics or a broker if needed

Kubernetes should come after that, when it becomes an operational multiplier instead of a complexity multiplier.
