package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import org.springframework.stereotype.Component;

@Component
public class WorkerDispatchPayloadFactory {

    private static final String TASK_DOWNLOAD = "DOWNLOAD";
    private static final String TASK_ANALYZE = "ANALYZE";
    private static final String TASK_EXPORT = "EXPORT";

    public WorkerDispatchPayload fromDownloadJob(VodJob job, Long executionId) {
        return new WorkerDispatchPayload(
                executionId,
                job.getId(),
                job.getProcessingVersion(),
                TASK_DOWNLOAD,
                job.getStorageVideoPath(),
                resolveSourceVideoReference(job),
                resolveSourceVideoDownloadUrl(job),
                job.getSourceType(),
                job.getSourceUrl(),
                null,
                null,
                null,
                null
        );
    }

    public WorkerDispatchPayload fromAnalyzeJob(VodJob job, Long executionId) {
        String videoPath = job.getStorageVideoPath();
        String videoReference = resolveSourceVideoReference(job);
        if ((videoPath == null || videoPath.isBlank()) && (videoReference == null || videoReference.isBlank())) {
            throw new IllegalStateException("Job " + job.getId() + " has no durable source video reference");
        }

        return new WorkerDispatchPayload(
                executionId,
                job.getId(),
                job.getProcessingVersion(),
                TASK_ANALYZE,
                videoPath,
                videoReference,
                resolveSourceVideoDownloadUrl(job),
                job.getSourceType(),
                job.getSourceUrl(),
                null,
                null,
                null,
                null
        );
    }

    public WorkerDispatchPayload fromExportCandidate(ClipCandidate candidate, Long executionId) {
        VodJob job = candidate.getVodJob();
        String videoPath = job.getStorageVideoPath();
        String videoReference = resolveSourceVideoReference(job);
        if ((videoPath == null || videoPath.isBlank()) && (videoReference == null || videoReference.isBlank())) {
            throw new IllegalStateException("Job " + job.getId() + " has no durable source video reference for export");
        }
        if (candidate.getExportedClipPath() == null || candidate.getExportedClipPath().isBlank()) {
            throw new IllegalStateException("Candidate " + candidate.getId() + " has no export artifact path");
        }

        return new WorkerDispatchPayload(
                executionId,
                job.getId(),
                job.getProcessingVersion(),
                TASK_EXPORT,
                videoPath,
                videoReference,
                resolveSourceVideoDownloadUrl(job),
                job.getSourceType(),
                job.getSourceUrl(),
                candidate.getId(),
                candidate.getStartSec(),
                candidate.getEndSec(),
                candidate.getExportedClipPath()
        );
    }

    private static String resolveSourceVideoReference(VodJob job) {
        String sourceVideoReference = job.getSourceVideoReference();
        if (sourceVideoReference != null && !sourceVideoReference.isBlank()) {
            return sourceVideoReference;
        }
        String storageVideoPath = job.getStorageVideoPath();
        return storageVideoPath == null || storageVideoPath.isBlank() ? null : storageVideoPath;
    }

    private static String resolveSourceVideoDownloadUrl(VodJob job) {
        String sourceVideoReference = resolveSourceVideoReference(job);
        if (sourceVideoReference == null || sourceVideoReference.isBlank()) {
            return null;
        }
        return "/api/internal/worker/jobs/" + job.getId() + "/source/file";
    }
}
