package com.peatroxd.streamcutproject.workertask;

import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.worker-task-retry")
@Getter
@Setter
public class WorkerTaskRetryProperties {

    private Map<WorkerTaskType, RetryPolicy> policies = defaultPolicies();

    public RetryPolicy resolvePolicy(WorkerTaskType taskType) {
        RetryPolicy policy = policies == null ? null : policies.get(taskType);
        RetryPolicy defaults = defaultPolicies().get(taskType);
        if (policy == null) {
            return defaults;
        }
        return new RetryPolicy(
                normalizeInteger(policy.getMaxAttempts(), defaults.getMaxAttempts()),
                normalizeDuration(policy.getInitialBackoff(), defaults.getInitialBackoff()),
                normalizeDuration(policy.getMaxBackoff(), defaults.getMaxBackoff())
        );
    }

    public int resolveMaxAttempts(WorkerTaskType taskType) {
        return resolvePolicy(taskType).getMaxAttempts();
    }

    public Duration resolveRetryBackoff(WorkerTaskType taskType, int attemptCount) {
        RetryPolicy policy = resolvePolicy(taskType);
        int attemptIndex = Math.max(1, attemptCount);
        Duration initialBackoff = policy.getInitialBackoff();
        Duration maxBackoff = policy.getMaxBackoff();

        Duration candidate = initialBackoff.multipliedBy(1L << Math.max(0, attemptIndex - 1));
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }

    private static Map<WorkerTaskType, RetryPolicy> defaultPolicies() {
        Map<WorkerTaskType, RetryPolicy> defaults = new EnumMap<>(WorkerTaskType.class);
        defaults.put(WorkerTaskType.DOWNLOAD, new RetryPolicy(3, Duration.ofSeconds(15), Duration.ofMinutes(2)));
        defaults.put(WorkerTaskType.ANALYZE, new RetryPolicy(3, Duration.ofSeconds(30), Duration.ofMinutes(5)));
        defaults.put(WorkerTaskType.EXPORT, new RetryPolicy(3, Duration.ofSeconds(15), Duration.ofMinutes(2)));
        return defaults;
    }

    private static Integer normalizeInteger(Integer configured, Integer fallback) {
        if (configured == null || configured < 1) {
            return fallback;
        }
        return configured;
    }

    private static Duration normalizeDuration(Duration configured, Duration fallback) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return fallback;
        }
        return configured;
    }

    @Getter
    @Setter
    public static class RetryPolicy {
        private Integer maxAttempts;
        private Duration initialBackoff;
        private Duration maxBackoff;

        public RetryPolicy() {
        }

        public RetryPolicy(Integer maxAttempts, Duration initialBackoff, Duration maxBackoff) {
            this.maxAttempts = maxAttempts;
            this.initialBackoff = initialBackoff;
            this.maxBackoff = maxBackoff;
        }
    }
}
