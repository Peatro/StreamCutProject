package com.peatroxd.streamcutproject.workerexecution;

import com.peatroxd.streamcutproject.vodjob.VodJob;
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
@Table(name = "worker_execution")
public class WorkerExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private VodJob vodJob;

    @Column(name = "processing_version", nullable = false)
    private Long processingVersion;

    @Column(name = "worker_id", nullable = false, length = 128)
    private String workerId;

    @Column(name = "worker_role", nullable = false, length = 32)
    private String workerRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private WorkerTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WorkerExecutionStatus status;

    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    protected WorkerExecution() {
    }

    public static WorkerExecution create(
            VodJob vodJob,
            Long processingVersion,
            String workerId,
            String workerRole,
            WorkerTaskType taskType,
            Long candidateId,
            Instant claimedAt
    ) {
        WorkerExecution execution = new WorkerExecution();
        execution.setVodJob(vodJob);
        execution.setProcessingVersion(processingVersion);
        execution.setWorkerId(workerId);
        execution.setWorkerRole(workerRole);
        execution.setTaskType(taskType);
        execution.setCandidateId(candidateId);
        execution.setStatus(WorkerExecutionStatus.CLAIMED);
        execution.setClaimedAt(claimedAt);
        execution.setLastHeartbeatAt(claimedAt);
        return execution;
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

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getWorkerRole() {
        return workerRole;
    }

    public void setWorkerRole(String workerRole) {
        this.workerRole = workerRole;
    }

    public WorkerTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(WorkerTaskType taskType) {
        this.taskType = taskType;
    }

    public WorkerExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(WorkerExecutionStatus status) {
        this.status = status;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
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
