package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import org.springframework.stereotype.Component;

@Component
public class WorkerDispatchPayloadFactory {

    private static final String TASK_ANALYZE = "ANALYZE";
    private static final String TASK_EXPORT = "EXPORT";

    public WorkerDispatchPayload fromJob(VodJob job) {
        String videoPath = job.getStorageVideoPath();
        if ("FILE".equals(job.getSourceType()) && (videoPath == null || videoPath.isBlank())) {
            throw new IllegalStateException("Job " + job.getId() + " has no storage video path");
        }

        return new WorkerDispatchPayload(
                job.getId(),
                TASK_ANALYZE,
                videoPath,
                job.getSourceType(),
                job.getSourceUrl(),
                null,
                null,
                null,
                null
        );
    }

    public WorkerDispatchPayload fromExportCandidate(ClipCandidate candidate) {
        VodJob job = candidate.getVodJob();
        String videoPath = job.getStorageVideoPath();
        if (videoPath == null || videoPath.isBlank()) {
            throw new IllegalStateException("Job " + job.getId() + " has no storage video path for export");
        }
        if (candidate.getExportedClipPath() == null || candidate.getExportedClipPath().isBlank()) {
            throw new IllegalStateException("Candidate " + candidate.getId() + " has no export artifact path");
        }

        return new WorkerDispatchPayload(
                job.getId(),
                TASK_EXPORT,
                videoPath,
                job.getSourceType(),
                job.getSourceUrl(),
                candidate.getId(),
                candidate.getStartSec(),
                candidate.getEndSec(),
                candidate.getExportedClipPath()
        );
    }
}
