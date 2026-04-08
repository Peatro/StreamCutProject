package com.peatroxd.streamcutproject.workerexecution;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkerExecutionRecoverySchedulerTest {

    @Mock
    private VodJobService vodJobService;

    @Test
    void schedulerDelegatesRecoveryToVodJobService() {
        WorkerExecutionRecoveryScheduler scheduler = new WorkerExecutionRecoveryScheduler(vodJobService);

        scheduler.recoverStaleExecutions();

        verify(vodJobService).recoverStaleExecutions();
    }
}
