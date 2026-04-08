package com.peatroxd.streamcutproject.retention;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.retention")
@Getter
@Setter
public class RetentionProperties {

    private static final Duration DEFAULT_SOURCE_RETENTION = Duration.ofDays(7);
    private static final Duration DEFAULT_ARTIFACT_RETENTION = Duration.ofDays(30);

    private Duration sourceRetention = DEFAULT_SOURCE_RETENTION;
    private Duration artifactRetention = DEFAULT_ARTIFACT_RETENTION;
    private String cleanupCron = "0 0 3 * * *";
    private String cleanupZone = "UTC";

    public Duration resolveSourceRetention() {
        return normalizeDuration(sourceRetention, DEFAULT_SOURCE_RETENTION);
    }

    public Duration resolveArtifactRetention() {
        return normalizeDuration(artifactRetention, DEFAULT_ARTIFACT_RETENTION);
    }

    private static Duration normalizeDuration(Duration configured, Duration fallback) {
        if (configured == null || configured.isNegative() || configured.isZero()) {
            return fallback;
        }
        return configured;
    }
}
