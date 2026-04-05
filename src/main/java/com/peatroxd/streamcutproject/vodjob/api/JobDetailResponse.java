package com.peatroxd.streamcutproject.vodjob.api;

import java.time.Instant;

public record JobDetailResponse(
        Long id,
        String sourceType,
        String sourceUrl,
        String originalFilename,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        Long durationSec,
        String language,
        String storageVideoPath,
        String storageAudioPath
) {
}
