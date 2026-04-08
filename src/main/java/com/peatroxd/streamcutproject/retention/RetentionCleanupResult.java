package com.peatroxd.streamcutproject.retention;

public record RetentionCleanupResult(
        int sourceFilesCleaned,
        int artifactFilesCleaned
) {
}
