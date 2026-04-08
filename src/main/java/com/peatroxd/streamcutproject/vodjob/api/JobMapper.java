package com.peatroxd.streamcutproject.vodjob.api;

import com.peatroxd.streamcutproject.vodjob.VodJob;

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

    public static JobListItemResponse toListItemResponse(VodJob job) {
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
                job.getProgressMessage()
        );
    }

    public static JobDetailResponse toDetailResponse(VodJob job) {
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
                job.getStorageAudioPath(),
                job.getProcessingVersion(),
                job.getCurrentWorkerId(),
                job.getLastWorkerHeartbeatAt(),
                job.getProgressPercent(),
                job.getProgressMessage()
        );
    }
}
