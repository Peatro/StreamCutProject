package com.peatroxd.streamcutproject.workerexecution;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.worker-execution")
public class WorkerExecutionProperties {

    private Duration staleTimeout = Duration.ofMinutes(2);
    private Duration reconcileInterval = Duration.ofSeconds(30);

    public Duration getStaleTimeout() {
        return staleTimeout;
    }

    public void setStaleTimeout(Duration staleTimeout) {
        this.staleTimeout = staleTimeout;
    }

    public Duration getReconcileInterval() {
        return reconcileInterval;
    }

    public void setReconcileInterval(Duration reconcileInterval) {
        this.reconcileInterval = reconcileInterval;
    }
}
