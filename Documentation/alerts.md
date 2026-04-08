# Alerts

This file documents a minimal Prometheus alert set for the `TASK-062` metrics surface.

Assumptions:

- counters are summed across backend instances
- database-backed gauges are deduplicated with `max`, not `sum`, because each backend instance reports the same shared queue state
- `streamcut.jobs.completed` means the job reached `READY_FOR_REVIEW`
- stale recovery is tracked with `streamcut.recovery.stale.actions` because `TASK-060` requeues stale work instead of marking the job terminally failed

```yaml
groups:
  - name: streamcut-observability
    rules:
      - alert: StreamCutQueueBacklogHigh
        expr: max by (job, queue_type) (streamcut_queue_depth{queue_type=~"download|processing"}) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "StreamCut queue backlog is growing"
          description: "The {{ $labels.queue_type }} queue has stayed above 10 jobs for 5 minutes."

      - alert: StreamCutWorkerFailureRateHigh
        expr: |
          (
            sum by (job) (rate(streamcut_jobs_failed_total{reason="worker_failure"}[15m]))
            /
            clamp_min(
              sum by (job) (rate(streamcut_jobs_completed_total[15m]))
              +
              sum by (job) (rate(streamcut_jobs_failed_total{reason="worker_failure"}[15m])),
              0.001
            )
          ) > 0.20
        for: 15m
        labels:
          severity: critical
        annotations:
          summary: "StreamCut worker failure rate is elevated"
          description: "Worker-reported failures exceeded 20% of completed-or-failed jobs during the last 15 minutes."

      - alert: StreamCutStaleRecoveryFrequent
        expr: increase(streamcut_recovery_stale_actions_total[15m]) > 3
        for: 0m
        labels:
          severity: warning
        annotations:
          summary: "StreamCut is repeatedly recovering stale worker executions"
          description: "More than 3 stale recovery actions fired in 15 minutes, which usually means missing workers or heartbeat stalls."
```

Optional follow-up:

- add a business-hours throughput alert once the team agrees on the production timezone and expected completion baseline
