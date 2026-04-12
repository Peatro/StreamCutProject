package com.peatroxd.streamcutproject.vodjob.api;

import java.time.Instant;

public record WorkerTaskResponse(
        Long id,
        String taskType,
        String status,
        Long processingVersion,
        Long candidateId,
        Integer attemptCount,
        Integer maxAttempts,
        Instant availableAt,
        Instant createdAt,
        Instant claimedAt,
        Instant lastHeartbeatAt,
        Instant finishedAt,
        String failureMessage,
        Instant deadLetteredAt,
        String deadLetterReason
) {
    public WorkerTaskResponse(
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
        this(
                id,
                taskType,
                status,
                processingVersion,
                candidateId,
                null,
                null,
                null,
                createdAt,
                claimedAt,
                lastHeartbeatAt,
                finishedAt,
                failureMessage,
                null,
                null
        );
    }
}
