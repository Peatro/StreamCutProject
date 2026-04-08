package com.peatroxd.streamcutproject.workerexecution;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkerExecutionRecoveryScheduler {

    private final VodJobService vodJobService;

    public WorkerExecutionRecoveryScheduler(VodJobService vodJobService) {
        this.vodJobService = vodJobService;
    }

    @Scheduled(fixedDelayString = "${app.worker-execution.reconcile-interval:30s}")
    public void recoverStaleExecutions() {
        vodJobService.recoverStaleExecutions();
    }
}
