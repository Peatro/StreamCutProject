package com.peatroxd.streamcutproject.retention;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetentionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupScheduler.class);
    private static final String METRIC_RETENTION_CLEANUP_DURATION = "streamcut.retention.cleanup.duration";

    private final RetentionCleanupService retentionCleanupService;
    private final MeterRegistry meterRegistry;

    @Scheduled(
            cron = "${app.retention.cleanup-cron:0 0 3 * * *}",
            zone = "${app.retention.cleanup-zone:UTC}"
    )
    public void cleanupExpiredFiles() {
        try {
            RetentionCleanupResult result = meterRegistry.timer(METRIC_RETENTION_CLEANUP_DURATION)
                    .recordCallable(retentionCleanupService::cleanupExpiredFiles);
            log.info(
                    "retention_cleanup_completed sourceFilesCleaned={} artifactFilesCleaned={}",
                    result.sourceFilesCleaned(),
                    result.artifactFilesCleaned()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Retention cleanup run failed", ex);
        }
    }
}
