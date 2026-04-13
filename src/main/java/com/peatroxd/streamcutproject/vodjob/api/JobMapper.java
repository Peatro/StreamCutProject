package com.peatroxd.streamcutproject.vodjob.api;

import com.peatroxd.streamcutproject.workerexecution.WorkerExecution;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
import com.peatroxd.streamcutproject.vodjob.VodJob;

import java.util.Optional;

public final class JobMapper {

    private JobMapper() {
    }

    public static JobSummaryResponse toSummaryResponse(VodJob job) {
        return new JobSummaryResponse(
                job.getId(),
                job.getStatus().name(),
                job.getSourceType(),
                job.getSourceUrl(),
                job.getOriginalFilename(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    public static JobListItemResponse toListItemResponse(
            VodJob job,
            Optional<WorkerExecution> latestExecution,
            Optional<WorkerTask> latestTask
    ) {
        return new JobListItemResponse(
                job.getId(),
                job.getSourceType(),
                job.getSourceUrl(),
                job.getOriginalFilename(),
                job.getStatus().name(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getDurationSec(),
                job.getLanguage(),
                job.getProgressPercent(),
                job.getProgressMessage(),
                latestExecution.map(JobMapper::toExecutionResponse).orElse(null),
                latestTask.map(JobMapper::toTaskResponse).orElse(null)
        );
    }

    public static JobDetailResponse toDetailResponse(
            VodJob job,
            Optional<WorkerExecution> latestExecution,
            Optional<WorkerTask> latestTask
    ) {
        return new JobDetailResponse(
                job.getId(),
                job.getSourceType(),
                job.getSourceUrl(),
                job.getOriginalFilename(),
                job.getStatus().name(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorMessage(),
                job.getDurationSec(),
                job.getLanguage(),
                job.getStorageVideoPath(),
                job.getSourceVideoReference(),
                job.getStorageAudioPath(),
                job.getProcessingVersion(),
                job.getCurrentWorkerId(),
                job.getLastWorkerHeartbeatAt(),
                job.getProgressPercent(),
                job.getProgressMessage(),
                latestExecution.map(JobMapper::toExecutionResponse).orElse(null),
                latestTask.map(JobMapper::toTaskResponse).orElse(null)
        );
    }

    public static WorkerExecutionResponse toExecutionResponse(WorkerExecution execution) {
        return new WorkerExecutionResponse(
                execution.getId(),
                execution.getTaskType().name(),
                execution.getStatus().name(),
                execution.getWorkerId(),
                execution.getWorkerRole(),
                execution.getProcessingVersion(),
                execution.getCandidateId(),
                execution.getClaimedAt(),
                execution.getLastHeartbeatAt(),
                execution.getFinishedAt(),
                execution.getFailureMessage(),
                execution.getWhisperDevice()
        );
    }

    public static WorkerTaskResponse toTaskResponse(WorkerTask task) {
        return new WorkerTaskResponse(
                task.getId(),
                task.getTaskType().name(),
                task.getStatus().name(),
                task.getProcessingVersion(),
                task.getCandidateId(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                task.getAvailableAt(),
                task.getCreatedAt(),
                task.getClaimedAt(),
                task.getLastHeartbeatAt(),
                task.getFinishedAt(),
                task.getFailureMessage(),
                task.getDeadLetteredAt(),
                task.getDeadLetterReason()
        );
    }
}
