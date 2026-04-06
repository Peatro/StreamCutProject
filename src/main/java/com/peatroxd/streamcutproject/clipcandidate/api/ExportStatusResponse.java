package com.peatroxd.streamcutproject.clipcandidate.api;

public record ExportStatusResponse(
        Long id,
        Long jobId,
        String status,
        String artifactPath,
        String moderationStatus,
        boolean exportReady
) {
}
