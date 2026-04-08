package com.peatroxd.streamcutproject.workerdispatch;

public record WorkerExportResultPayload(
        Long jobId,
        String workerId,
        Long processingVersion,
        Long candidateId,
        String artifactPath
) {
}
