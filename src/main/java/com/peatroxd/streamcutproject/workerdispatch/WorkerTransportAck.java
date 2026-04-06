package com.peatroxd.streamcutproject.workerdispatch;

public record WorkerTransportAck(
        Long jobId,
        String status
) {
}
