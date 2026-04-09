package com.peatroxd.streamcutproject.workerexecution;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkerExecutionRecoveryScheduler {

    private static final String METRIC_RECOVERY_RECONCILE_DURATION = "streamcut.recovery.reconcile.duration";

    private final VodJobService vodJobService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${app.worker-execution.reconcile-interval:30s}")
    public void recoverStaleExecutions() {
        meterRegistry.timer(METRIC_RECOVERY_RECONCILE_DURATION)
                .record(() -> vodJobService.recoverStaleExecutions());
    }
}
