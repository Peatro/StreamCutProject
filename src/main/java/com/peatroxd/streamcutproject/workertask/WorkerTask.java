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

import java.time.Instant;

@Entity
@Table(name = "worker_task")
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

    protected WorkerTask() {
    }

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
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    public void markClaimed(Instant now) {
        this.status = WorkerTaskStatus.CLAIMED;
        this.claimedAt = now;
        this.lastHeartbeatAt = now;
        this.updatedAt = now;
        this.finishedAt = null;
        this.failureMessage = null;
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
    }

    public void markSucceeded(Instant now) {
        this.status = WorkerTaskStatus.SUCCEEDED;
        this.updatedAt = now;
        this.lastHeartbeatAt = now;
        this.finishedAt = now;
        this.failureMessage = null;
    }

    public void markFailed(Instant now, String failureMessage) {
        this.status = WorkerTaskStatus.FAILED;
        this.updatedAt = now;
        this.lastHeartbeatAt = now;
        this.finishedAt = now;
        this.failureMessage = failureMessage;
    }

    public void markCanceled(Instant now, String failureMessage) {
        this.status = WorkerTaskStatus.CANCELED;
        this.updatedAt = now;
        this.lastHeartbeatAt = now;
        this.finishedAt = now;
        this.failureMessage = failureMessage;
    }

    public Long getId() {
        return id;
    }

    public VodJob getVodJob() {
        return vodJob;
    }

    public void setVodJob(VodJob vodJob) {
        this.vodJob = vodJob;
    }

    public Long getProcessingVersion() {
        return processingVersion;
    }

    public void setProcessingVersion(Long processingVersion) {
        this.processingVersion = processingVersion;
    }

    public WorkerTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(WorkerTaskType taskType) {
        this.taskType = taskType;
    }

    public WorkerTaskStatus getStatus() {
        return status;
    }

    public void setStatus(WorkerTaskStatus status) {
        this.status = status;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }
}
