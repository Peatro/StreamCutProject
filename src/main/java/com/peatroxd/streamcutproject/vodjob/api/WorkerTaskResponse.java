package com.peatroxd.streamcutproject.vodjob.api;

import java.time.Instant;

public record WorkerTaskResponse(
        Long id,
        String taskType,
        String status,
        Long processingVersion,
        Long candidateId,
        Instant createdAt,
        Instant claimedAt,
        Instant lastHeartbeatAt,
        Instant finishedAt,
        String failureMessage
) {
}
