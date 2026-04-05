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
                job.getLanguage()
        );
    }
}
