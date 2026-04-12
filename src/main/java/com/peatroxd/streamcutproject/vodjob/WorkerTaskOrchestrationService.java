package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.storage.PathSafety;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionProperties;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
import com.peatroxd.streamcutproject.workertask.WorkerTaskRepository;
import com.peatroxd.streamcutproject.workertask.WorkerTaskRetryProperties;
import com.peatroxd.streamcutproject.workertask.WorkerTaskStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkerTaskOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerTaskOrchestrationService.class);

    private final StorageProperties storageProperties;
    private final WorkerExecutionProperties workerExecutionProperties;
    private final WorkerTaskRetryProperties workerTaskRetryProperties;
    private final WorkerTaskRepository workerTaskRepository;

    public Optional<WorkerTask> selectNextDownloadTask(Instant now) {
        return claimableTask(
                workerTaskRepository.findAllByTaskTypeAndStatusOrderByAvailableAtAscIdAsc(
                        WorkerTaskType.DOWNLOAD,
                        WorkerTaskStatus.QUEUED
                ),
                now
        );
    }

    public Optional<WorkerTask> selectNextProcessingTask(Instant now) {
        Optional<WorkerTask> queuedExportTask = claimableTask(
                workerTaskRepository.findAllByTaskTypeAndStatusOrderByAvailableAtAscIdAsc(
                        WorkerTaskType.EXPORT,
                        WorkerTaskStatus.QUEUED
                ),
                now
        );
        if (queuedExportTask.isPresent()) {
            return queuedExportTask;
        }

        return workerTaskRepository.findAllByTaskTypeAndStatusOrderByAvailableAtAscIdAsc(
                        WorkerTaskType.ANALYZE,
                        WorkerTaskStatus.QUEUED
                ).stream()
                .filter(task -> isTaskClaimable(task, now))
                .filter(task -> isAnalyzeSourceVideoReady(task.getVodJob(), task.getId()))
                .findFirst();
    }

    public void queueDownloadJob(VodJob job, Instant queuedAt) {
        JobProjection.applyQueuedTask(job, WorkerTaskType.DOWNLOAD, queuedAt, "Queued for download worker");
        job.setLastWorkerHeartbeatAt(null);
        job.setFinishedAt(null);
        job.setErrorMessage(null);
    }

    public void queueTaskForRetry(VodJob job, WorkerTaskType taskType, Instant now, String progressMessage) {
        JobProjection.applyQueuedTask(job, taskType, now, progressMessage);
        job.setFinishedAt(null);
        job.setErrorMessage(null);
    }

    public WorkerTask prepareWorkerTask(
            VodJob job,
            Long processingVersion,
            WorkerTaskType taskType,
            Long candidateId,
            Instant now
    ) {
        Optional<WorkerTask> existingTask = candidateId == null
                ? workerTaskRepository.findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndStatusInOrderByIdAsc(
                        job.getId(),
                        processingVersion,
                        taskType,
                        List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
                )
                : workerTaskRepository.findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndCandidateIdAndStatusInOrderByIdAsc(
                        job.getId(),
                        processingVersion,
                        taskType,
                        candidateId,
                        List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
                );
        WorkerTask task = existingTask.orElseGet(() -> WorkerTask.createQueued(job, processingVersion, taskType, candidateId, now));
        applyRetryPolicy(task, taskType, now);
        return task;
    }

    public boolean applyTaskFailurePolicy(WorkerTask task, Instant now, String failureSummary) {
        if (task.getMaxAttempts() == null || task.getMaxAttempts() < 1) {
            task.setMaxAttempts(workerTaskRetryProperties.resolveMaxAttempts(task.getTaskType()));
        }
        if (isRetryBudgetExhausted(task)) {
            task.markDeadLettered(now, failureSummary, deadLetterMessage(task, failureSummary));
            return false;
        }

        Instant availableAt = now.plus(workerTaskRetryProperties.resolveRetryBackoff(
                task.getTaskType(),
                task.getAttemptCountOrZero()
        ));
        task.markQueuedForRetry(now, availableAt, failureSummary);
        return true;
    }

    public boolean isTaskStale(WorkerTask task, Instant now) {
        Instant lastHeartbeatAt = task.getLastHeartbeatAt();
        if (lastHeartbeatAt == null) {
            return false;
        }

        Instant staleBefore = now.minus(workerExecutionProperties.resolveStaleTimeout(task.getTaskType()));
        return lastHeartbeatAt.isBefore(staleBefore);
    }

    public String retryScheduledProgressMessage(WorkerTask task) {
        int nextAttemptNumber = task.getAttemptCountOrZero() + 1;
        return switch (task.getTaskType()) {
            case DOWNLOAD -> "Download worker retry attempt " + nextAttemptNumber + " is queued";
            case ANALYZE -> "Processing worker retry attempt " + nextAttemptNumber + " is queued";
            case EXPORT -> "Export worker retry attempt " + nextAttemptNumber + " is queued";
        };
    }

    public String deadLetterMessage(WorkerTask task, String failureSummary) {
        return failureSummary + " (retry budget exhausted after " + task.getAttemptCountOrZero() + " attempts)";
    }

    private Optional<WorkerTask> claimableTask(List<WorkerTask> tasks, Instant now) {
        return tasks.stream()
                .filter(task -> isTaskClaimable(task, now))
                .findFirst();
    }

    private boolean isTaskClaimable(WorkerTask task, Instant now) {
        Instant availableAt = task.getAvailableAt();
        return availableAt == null || !availableAt.isAfter(now);
    }

    private boolean isAnalyzeSourceVideoReady(VodJob job, Long taskId) {
        String storageVideoPath = job.getStorageVideoPath();
        if (storageVideoPath == null || storageVideoPath.isBlank()) {
            log.warn("analysis_claim_deferred_missing_video_path jobId={} taskId={}", job.getId(), taskId);
            return false;
        }

        try {
            Path normalizedPath = PathSafety.requireWithinRoot(
                    storageProperties.getLocalRoot(),
                    Path.of(storageVideoPath),
                    "job storage video path"
            ).toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedPath)) {
                log.warn(
                        "analysis_claim_deferred_missing_video jobId={} taskId={} videoPath={}",
                        job.getId(),
                        taskId,
                        normalizeArtifactPath(normalizedPath.toString())
                );
                return false;
            }
            job.setStorageVideoPath(normalizeArtifactPath(normalizedPath.toString()));
            return true;
        } catch (InvalidPathException | ResponseStatusException ex) {
            log.warn(
                    "analysis_claim_deferred_invalid_video_path jobId={} taskId={} videoPath={} message={}",
                    job.getId(),
                    taskId,
                    storageVideoPath,
                    ex.getMessage()
            );
            return false;
        }
    }

    private boolean isRetryBudgetExhausted(WorkerTask task) {
        return task.getAttemptCountOrZero() >= effectiveMaxAttempts(task);
    }

    private int effectiveMaxAttempts(WorkerTask task) {
        Integer configuredMaxAttempts = task.getMaxAttempts();
        return configuredMaxAttempts == null || configuredMaxAttempts < 1
                ? workerTaskRetryProperties.resolveMaxAttempts(task.getTaskType())
                : configuredMaxAttempts;
    }

    private void applyRetryPolicy(WorkerTask task, WorkerTaskType taskType, Instant now) {
        if (task.getAttemptCount() == null) {
            task.setAttemptCount(0);
        }
        if (task.getMaxAttempts() == null || task.getMaxAttempts() < 1) {
            task.setMaxAttempts(workerTaskRetryProperties.resolveMaxAttempts(taskType));
        }
        if (task.getStatus() == WorkerTaskStatus.QUEUED && task.getAvailableAt() == null) {
            task.setAvailableAt(now);
        }
    }

    private static String normalizeArtifactPath(String path) {
        return path.replace('\\', '/');
    }
}
