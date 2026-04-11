package com.peatroxd.streamcutproject.vodjob.api;

import java.time.Instant;

public record WorkerExecutionResponse(
        Long id,
        String taskType,
        String status,
        String workerId,
        String workerRole,
        Long processingVersion,
        Long candidateId,
        Instant claimedAt,
        Instant lastHeartbeatAt,
        Instant finishedAt,
        String failureMessage,
        String whisperDevice
) {
}
