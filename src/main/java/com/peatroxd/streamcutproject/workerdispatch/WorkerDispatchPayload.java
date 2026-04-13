package com.peatroxd.streamcutproject.workerdispatch;

public record WorkerDispatchPayload(
        Long executionId,
        Long jobId,
        Long processingVersion,
        String taskType,
        String videoPath,
        String videoReference,
        String videoDownloadUrl,
        String sourceType,
        String sourceUrl,
        Long candidateId,
        Double clipStartSec,
        Double clipEndSec,
        String artifactPath
) {
}
