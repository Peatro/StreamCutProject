package com.peatroxd.streamcutproject.workerexecution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.worker-execution")
@Getter
@Setter
public class WorkerExecutionProperties {

    private static final Duration DEFAULT_STALE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEFAULT_ANALYZE_STALE_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration DEFAULT_RECONCILE_INTERVAL = Duration.ofSeconds(30);

    private Duration staleTimeout = DEFAULT_STALE_TIMEOUT;
    private Map<WorkerTaskType, Duration> staleTimeoutOverrides = defaultStaleTimeoutOverrides();
    private Duration reconcileInterval = DEFAULT_RECONCILE_INTERVAL;

    public Duration resolveStaleTimeout() {
        return normalizeDuration(staleTimeout, DEFAULT_STALE_TIMEOUT);
    }

    public Duration resolveStaleTimeout(WorkerTaskType taskType) {
        Duration override = staleTimeoutOverrides == null ? null : staleTimeoutOverrides.get(taskType);
        return normalizeDuration(override, resolveStaleTimeout());
    }

    public Map<WorkerTaskType, Duration> resolveStaleTimeouts() {
        Map<WorkerTaskType, Duration> resolved = new EnumMap<>(WorkerTaskType.class);
        for (WorkerTaskType taskType : WorkerTaskType.values()) {
            resolved.put(taskType, resolveStaleTimeout(taskType));
        }
        return Map.copyOf(resolved);
    }

    public Duration resolveReconcileInterval() {
        return normalizeDuration(reconcileInterval, DEFAULT_RECONCILE_INTERVAL);
    }

    private static Duration normalizeDuration(Duration configured, Duration fallback) {
        if (configured == null || configured.isNegative() || configured.isZero()) {
            return fallback;
        }
        return configured;
    }

    private static Map<WorkerTaskType, Duration> defaultStaleTimeoutOverrides() {
        Map<WorkerTaskType, Duration> defaults = new EnumMap<>(WorkerTaskType.class);
        defaults.put(WorkerTaskType.ANALYZE, DEFAULT_ANALYZE_STALE_TIMEOUT);
        return defaults;
    }

}
