package com.peatroxd.streamcutproject.workerdispatch;

public record WorkerDispatchPayload(
        Long jobId,
        String taskType,
        String videoPath,
        String sourceType,
        String sourceUrl,
        Long candidateId,
        Double clipStartSec,
        Double clipEndSec,
        String artifactPath
) {
}
