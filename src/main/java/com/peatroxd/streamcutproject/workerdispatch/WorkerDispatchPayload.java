package com.peatroxd.streamcutproject.workerdispatch;

public record WorkerDispatchPayload(
        Long jobId,
        String videoPath,
        String sourceType,
        String sourceUrl
) {
}
