package com.peatroxd.streamcutproject.workerexecution;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkerExecutionRecoveryScheduler {

    private final VodJobService vodJobService;

    @Scheduled(fixedDelayString = "${app.worker-execution.reconcile-interval:30s}")
    public void recoverStaleExecutions() {
        vodJobService.recoverStaleExecutions();
    }
}
