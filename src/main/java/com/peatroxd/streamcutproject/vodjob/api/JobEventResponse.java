package com.peatroxd.streamcutproject.vodjob.api;

import java.time.Instant;

public record JobEventResponse(
        Long id,
        String eventType,
        String message,
        Instant createdAt
) {
}
