package com.peatroxd.streamcutproject.workerexecution;

import com.peatroxd.streamcutproject.vodjob.VodJob;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
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
@Table(name = "worker_execution")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkerExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private VodJob vodJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private WorkerTask workerTask;

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

    public static WorkerExecution create(
            VodJob vodJob,
            WorkerTask workerTask,
            Long processingVersion,
            String workerId,
            String workerRole,
            WorkerTaskType taskType,
            Long candidateId,
            Instant claimedAt
    ) {
        WorkerExecution execution = new WorkerExecution();
        execution.setVodJob(vodJob);
        execution.setWorkerTask(workerTask);
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
}
