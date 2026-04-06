package com.peatroxd.streamcutproject.workerdispatch;

public record WorkerExportResultPayload(
        Long jobId,
        Long candidateId,
        String artifactPath
) {
}
