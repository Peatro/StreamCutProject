package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
import com.peatroxd.streamcutproject.workertask.WorkerTaskStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class JobProjection {

    private JobProjection() {
    }

    public static void applyQueuedTask(VodJob job, WorkerTaskType taskType, Instant now, String progressMessage) {
        JobStatus queuedStatus = queuedStatusFor(taskType);
        job.setStatus(queuedStatus);
        job.setUpdatedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(queuedStatus));
        job.setProgressMessage(progressMessage);
    }

    public static void applyClaimedTask(VodJob job, WorkerTaskType taskType, String workerId, Instant now) {
        JobStatus claimedStatus = claimedStatusFor(taskType);
        job.setStatus(claimedStatus);
        job.setUpdatedAt(now);
        job.setCurrentWorkerId(workerId);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(claimedStatus));
        job.setProgressMessage(claimedProgressMessageFor(taskType));
    }

    public static void applyProgress(VodJob job, JobStatus status, Instant now, int progressPercent, String progressMessage) {
        job.setStatus(status);
        job.setUpdatedAt(now);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercent);
        job.setProgressMessage(progressMessage);
    }

    public static void applyReadyForReview(VodJob job, Instant now) {
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(JobStatus.READY_FOR_REVIEW));
        job.setProgressMessage("Analysis completed and candidates are ready for review");
        job.setErrorMessage(null);
    }

    public static void applyCompletedExport(VodJob job, Instant now) {
        job.setStatus(JobStatus.COMPLETED);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(JobStatus.COMPLETED));
        job.setProgressMessage("Export completed and clip artifact is ready");
        job.setErrorMessage(null);
    }

    public static void applyCanceled(VodJob job, Instant now, String errorMessage) {
        job.setStatus(JobStatus.CANCELED);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(JobStatus.CANCELED));
        job.setProgressMessage("Worker run canceled by operator");
        job.setErrorMessage(errorMessage);
    }

    public static void applyFailure(VodJob job, Instant now, String errorMessage) {
        job.setStatus(JobStatus.FAILED);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(JobStatus.FAILED));
        job.setProgressMessage("Worker run failed");
        job.setErrorMessage(errorMessage);
    }

    public static void recomputeFromTasks(VodJob job, List<WorkerTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        if (job.getStatus() == JobStatus.CANCELED || job.getStatus() == JobStatus.FAILED) {
            return;
        }

        WorkerTask projectedTask = resolveProjectedTask(tasks);
        if (projectedTask == null) {
            return;
        }

        Instant projectionTime = latestMeaningfulTime(job, projectedTask);
        switch (projectedTask.getStatus()) {
            case QUEUED -> applyQueuedTask(job, projectedTask.getTaskType(), projectionTime, queuedMessageFor(projectedTask.getTaskType()));
            case CLAIMED -> applyClaimedTask(job, projectedTask.getTaskType(), job.getCurrentWorkerId(), projectionTime);
            case RUNNING -> {
                if (projectedTask.getTaskType() == WorkerTaskType.ANALYZE && isAnalyzeStage(job.getStatus())) {
                    job.setLastWorkerHeartbeatAt(projectionTime);
                    job.setUpdatedAt(projectionTime);
                } else {
                    applyClaimedTask(job, projectedTask.getTaskType(), job.getCurrentWorkerId(), projectionTime);
                }
            }
            case SUCCEEDED -> applySucceededTask(job, projectedTask, projectionTime);
            case FAILED, CANCELED -> {
                // Terminal task state does not override explicit aggregate job state on read.
            }
        }
    }

    public static int progressPercentFor(JobStatus status) {
        return switch (status) {
            case NEW -> 0;
            case QUEUED_FOR_DOWNLOAD -> 5;
            case DOWNLOADING -> 18;
            case QUEUED_FOR_PROCESSING -> 28;
            case EXTRACTING_AUDIO -> 36;
            case TRANSCRIBING -> 48;
            case DETECTING_SILENCE -> 68;
            case ANALYZING_WINDOWS -> 84;
            case GENERATING_CANDIDATES -> 94;
            case READY_FOR_REVIEW, COMPLETED -> 100;
            case EXPORTING_CLIP -> 92;
            case CANCELED, FAILED -> 0;
        };
    }

    private static JobStatus queuedStatusFor(WorkerTaskType taskType) {
        return switch (taskType) {
            case DOWNLOAD -> JobStatus.QUEUED_FOR_DOWNLOAD;
            case ANALYZE -> JobStatus.QUEUED_FOR_PROCESSING;
            case EXPORT -> JobStatus.EXPORTING_CLIP;
        };
    }

    private static JobStatus claimedStatusFor(WorkerTaskType taskType) {
        return switch (taskType) {
            case DOWNLOAD -> JobStatus.DOWNLOADING;
            case ANALYZE -> JobStatus.EXTRACTING_AUDIO;
            case EXPORT -> JobStatus.EXPORTING_CLIP;
        };
    }

    private static String claimedProgressMessageFor(WorkerTaskType taskType) {
        return switch (taskType) {
            case DOWNLOAD -> "Download worker claimed job and started source materialization";
            case ANALYZE -> "Processing worker claimed job and started analysis pipeline";
            case EXPORT -> "Worker claimed export task";
        };
    }

    private static void applySucceededTask(VodJob job, WorkerTask latestTask, Instant projectionTime) {
        switch (latestTask.getTaskType()) {
            case DOWNLOAD -> {
                if (!isPostDownloadState(job.getStatus())) {
                    applyQueuedTask(job, WorkerTaskType.ANALYZE, projectionTime, "Source video is ready and queued for processing");
                }
            }
            case ANALYZE -> {
                if (job.getStatus() != JobStatus.EXPORTING_CLIP && job.getStatus() != JobStatus.COMPLETED) {
                    applyReadyForReview(job, projectionTime);
                }
            }
            case EXPORT -> applyCompletedExport(job, projectionTime);
        }
    }

    private static WorkerTask resolveProjectedTask(List<WorkerTask> tasks) {
        return tasks.stream()
                .filter(task -> task.getStatus() == WorkerTaskStatus.RUNNING
                        || task.getStatus() == WorkerTaskStatus.CLAIMED
                        || task.getStatus() == WorkerTaskStatus.QUEUED)
                .max(Comparator
                        .comparingInt((WorkerTask task) -> activeStatusPriority(task.getStatus()))
                        .thenComparingInt(task -> taskTypePriority(task.getTaskType()))
                        .thenComparing(WorkerTask::getId, Comparator.nullsFirst(Long::compareTo)))
                .orElseGet(() -> tasks.stream()
                        .max(Comparator.comparing(WorkerTask::getId, Comparator.nullsFirst(Long::compareTo)))
                        .orElse(null));
    }

    private static int activeStatusPriority(WorkerTaskStatus status) {
        return switch (status) {
            case RUNNING -> 3;
            case CLAIMED -> 2;
            case QUEUED -> 1;
            case SUCCEEDED, FAILED, CANCELED -> 0;
        };
    }

    private static int taskTypePriority(WorkerTaskType taskType) {
        return switch (taskType) {
            case EXPORT -> 3;
            case ANALYZE -> 2;
            case DOWNLOAD -> 1;
        };
    }

    private static boolean isAnalyzeStage(JobStatus status) {
        return status == JobStatus.EXTRACTING_AUDIO
                || status == JobStatus.TRANSCRIBING
                || status == JobStatus.DETECTING_SILENCE
                || status == JobStatus.ANALYZING_WINDOWS
                || status == JobStatus.GENERATING_CANDIDATES;
    }

    private static boolean isPostDownloadState(JobStatus status) {
        return status == JobStatus.QUEUED_FOR_PROCESSING
                || isAnalyzeStage(status)
                || status == JobStatus.READY_FOR_REVIEW
                || status == JobStatus.EXPORTING_CLIP
                || status == JobStatus.COMPLETED;
    }

    private static String queuedMessageFor(WorkerTaskType taskType) {
        return switch (taskType) {
            case DOWNLOAD -> "Queued for download worker";
            case ANALYZE -> "Source video is ready and queued for processing";
            case EXPORT -> "Queued for clip export";
        };
    }

    private static Instant latestMeaningfulTime(VodJob job, WorkerTask latestTask) {
        if (latestTask.getFinishedAt() != null) {
            return latestTask.getFinishedAt();
        }
        if (latestTask.getLastHeartbeatAt() != null) {
            return latestTask.getLastHeartbeatAt();
        }
        if (latestTask.getClaimedAt() != null) {
            return latestTask.getClaimedAt();
        }
        if (latestTask.getUpdatedAt() != null) {
            return latestTask.getUpdatedAt();
        }
        if (latestTask.getCreatedAt() != null) {
            return latestTask.getCreatedAt();
        }
        return job.getUpdatedAt();
    }
}
