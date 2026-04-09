package com.peatroxd.streamcutproject.vodjob.api;

import java.time.Instant;

public record JobListItemResponse(
        Long id,
        String sourceType,
        String sourceUrl,
        String originalFilename,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Long durationSec,
        String language,
        Integer progressPercent,
        String progressMessage,
        WorkerExecutionResponse latestExecution,
        WorkerTaskResponse latestTask
) {
}
