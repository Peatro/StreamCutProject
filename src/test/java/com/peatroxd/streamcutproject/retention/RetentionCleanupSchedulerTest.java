package com.peatroxd.streamcutproject.retention;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionCleanupSchedulerTest {

    @Mock
    private RetentionCleanupService retentionCleanupService;

    @Test
    void schedulerDelegatesCleanupToRetentionService() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RetentionCleanupScheduler scheduler = new RetentionCleanupScheduler(retentionCleanupService, meterRegistry);
        when(retentionCleanupService.cleanupExpiredFiles()).thenReturn(new RetentionCleanupResult(2, 3));

        scheduler.cleanupExpiredFiles();

        verify(retentionCleanupService).cleanupExpiredFiles();
        assertThat(meterRegistry.get("streamcut.retention.cleanup.duration").timer().count()).isEqualTo(1L);
    }
}
