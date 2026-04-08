package com.peatroxd.streamcutproject.workerexecution;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkerExecutionRecoverySchedulerTest {

    @Mock
    private VodJobService vodJobService;

    @Test
    void schedulerDelegatesRecoveryToVodJobService() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        WorkerExecutionRecoveryScheduler scheduler = new WorkerExecutionRecoveryScheduler(vodJobService, meterRegistry);
        when(vodJobService.recoverStaleExecutions()).thenReturn(2);

        scheduler.recoverStaleExecutions();

        verify(vodJobService).recoverStaleExecutions();
        assertThat(meterRegistry.get("streamcut.recovery.reconcile.duration").timer().count()).isEqualTo(1L);
    }
}
