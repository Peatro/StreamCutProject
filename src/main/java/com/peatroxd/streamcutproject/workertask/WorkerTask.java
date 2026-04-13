package com.peatroxd.streamcutproject.workertask;

import com.peatroxd.streamcutproject.vodjob.VodJob;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "worker_task")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkerTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private VodJob vodJob;

    @Column(name = "processing_version", nullable = false)
    private Long processingVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private WorkerTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WorkerTaskStatus status;

    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(name = "dead_letter_reason", length = 1000)
    private String deadLetterReason;

    public static WorkerTask createQueued(
            VodJob vodJob,
            Long processingVersion,
            WorkerTaskType taskType,
            Long candidateId,
            Instant now
    ) {
        WorkerTask task = new WorkerTask();
        task.setVodJob(vodJob);
        task.setProcessingVersion(processingVersion);
        task.setTaskType(taskType);
        task.setCandidateId(candidateId);
        task.setStatus(WorkerTaskStatus.QUEUED);
        task.setAttemptCount(0);
        task.setAvailableAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    public void markClaimed(Instant now) {
        this.status = WorkerTaskStatus.CLAIMED;
        this.attemptCount = getAttemptCountOrZero() + 1;
        this.claimedAt = now;
        this.lastHeartbeatAt = now;
        this.updatedAt = now;
        this.finishedAt = null;
        this.failureMessage = null;
        this.availableAt = null;
        this.deadLetteredAt = null;
        this.deadLetterReason = null;
    }

    public void markRunning(Instant now) {
        this.status = WorkerTaskStatus.RUNNING;
        this.lastHeartbeatAt = now;
        this.updatedAt = now;
    }

    public void markQueued(Instant now) {
        this.status = WorkerTaskStatus.QUEUED;
        this.updatedAt = now;
        this.claimedAt = null;
        this.lastHeartbeatAt = null;
        this.finishedAt = null;
        this.availableAt = now;
        this.failureMessage = null;
        this.deadLetteredAt = null;
        this.deadLetterReason = null;
    }

    public void markQueuedForRetry(Instant now, Instant availableAt, String failureMessage) {
        this.status = WorkerTaskStatus.QUEUED;
        this.updatedAt = now;
        this.claimedAt = null;
        this.lastHeartbeatAt = null;
        this.finishedAt = null;
        this.availableAt = availableAt;
        this.failureMessage = failureMessage;
        this.deadLetteredAt = null;
        this.deadLetterReason = null;
    }

    public void markSucceeded(Instant now) {
        this.status = WorkerTaskStatus.SUCCEEDED;
        this.updatedAt = now;
        this.lastHeartbeatAt = now;
        this.finishedAt = now;
        this.failureMessage = null;
        this.availableAt = null;
        this.deadLetteredAt = null;
        this.deadLetterReason = null;
    }

    public void markFailed(Instant now, String failureMessage) {
        this.status = WorkerTaskStatus.FAILED;
        this.updatedAt = now;
        this.lastHeartbeatAt = now;
        this.finishedAt = now;
        this.failureMessage = failureMessage;
        this.availableAt = null;
        this.deadLetteredAt = null;
        this.deadLetterReason = null;
    }

    public void markCanceled(Instant now, String failureMessage) {
        this.status = WorkerTaskStatus.CANCELED;
        this.updatedAt = now;
        this.lastHeartbeatAt = now;
        this.finishedAt = now;
        this.failureMessage = failureMessage;
        this.availableAt = null;
        this.deadLetteredAt = null;
        this.deadLetterReason = null;
    }

    public void markDeadLettered(Instant now, String failureMessage, String deadLetterReason) {
        this.status = WorkerTaskStatus.DEAD_LETTERED;
        this.updatedAt = now;
        this.lastHeartbeatAt = now;
        this.finishedAt = now;
        this.failureMessage = failureMessage;
        this.availableAt = null;
        this.deadLetteredAt = now;
        this.deadLetterReason = deadLetterReason;
    }

    public int getAttemptCountOrZero() {
        return attemptCount == null ? 0 : attemptCount;
    }
}
