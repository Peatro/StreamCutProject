package com.peatroxd.streamcutproject.vodjob.api;

import java.time.Instant;

public record JobSummaryResponse(
        Long id,
        String status,
        String sourceType,
        String sourceUrl,
        String originalFilename,
        Instant createdAt,
        Instant updatedAt
) {
}
