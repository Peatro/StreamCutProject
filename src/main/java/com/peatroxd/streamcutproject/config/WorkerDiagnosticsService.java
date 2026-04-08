package com.peatroxd.streamcutproject.config;

import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionProperties;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
import com.peatroxd.streamcutproject.workertask.WorkerTaskRepository;
import com.peatroxd.streamcutproject.workertask.WorkerTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkerDiagnosticsService {

    private final WorkerTaskRepository workerTaskRepository;
    private final WorkerExecutionProperties workerExecutionProperties;

    public WorkerDiagnosticsResponse snapshot() {
        Instant now = Instant.now();
        Duration staleTimeout = resolveStaleTimeout();
        Instant staleBefore = now.minus(staleTimeout);

        List<WorkerTask> relevantTasks = workerTaskRepository.findAllByStatusInOrderByIdAsc(
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        );

        RoleDiagnosticsAccumulator download = new RoleDiagnosticsAccumulator("download");
        RoleDiagnosticsAccumulator processing = new RoleDiagnosticsAccumulator("processing");

        for (WorkerTask task : relevantTasks) {
            RoleDiagnosticsAccumulator accumulator = roleFor(task.getTaskType()) == WorkerRole.DOWNLOAD
                    ? download
                    : processing;
            accumulator.accept(task, staleBefore);
        }

        return new WorkerDiagnosticsResponse(
                "UP",
                staleTimeout.getSeconds(),
                resolveReconcileInterval().getSeconds(),
                Map.of(
                        "download", download.build(),
                        "processing", processing.build()
                )
        );
    }

    private Duration resolveStaleTimeout() {
        Duration configured = workerExecutionProperties.getStaleTimeout();
        if (configured == null || configured.isNegative() || configured.isZero()) {
            return Duration.ofMinutes(2);
        }
        return configured;
    }

    private Duration resolveReconcileInterval() {
        Duration configured = workerExecutionProperties.getReconcileInterval();
        if (configured == null || configured.isNegative() || configured.isZero()) {
            return Duration.ofSeconds(30);
        }
        return configured;
    }

    private static WorkerRole roleFor(WorkerTaskType taskType) {
        return taskType == WorkerTaskType.DOWNLOAD ? WorkerRole.DOWNLOAD : WorkerRole.PROCESSING;
    }

    private enum WorkerRole {
        DOWNLOAD,
        PROCESSING
    }

    private static final class RoleDiagnosticsAccumulator {
        private final String role;
        private int queued;
        private int claimed;
        private int running;
        private int stale;
        private Instant oldestHeartbeatAt;
        private final Map<WorkerTaskType, Integer> queuedByTaskType = new EnumMap<>(WorkerTaskType.class);
        private final Map<WorkerTaskType, Integer> activeByTaskType = new EnumMap<>(WorkerTaskType.class);

        private RoleDiagnosticsAccumulator(String role) {
            this.role = role;
        }

        private void accept(WorkerTask task, Instant staleBefore) {
            if (task.getStatus() == WorkerTaskStatus.QUEUED) {
                queued++;
                queuedByTaskType.merge(task.getTaskType(), 1, Integer::sum);
                return;
            }

            if (task.getStatus() == WorkerTaskStatus.CLAIMED) {
                claimed++;
                activeByTaskType.merge(task.getTaskType(), 1, Integer::sum);
            } else if (task.getStatus() == WorkerTaskStatus.RUNNING) {
                running++;
                activeByTaskType.merge(task.getTaskType(), 1, Integer::sum);
            }

            Instant lastHeartbeatAt = task.getLastHeartbeatAt();
            if (lastHeartbeatAt != null) {
                if (oldestHeartbeatAt == null || lastHeartbeatAt.isBefore(oldestHeartbeatAt)) {
                    oldestHeartbeatAt = lastHeartbeatAt;
                }
                if (lastHeartbeatAt.isBefore(staleBefore)) {
                    stale++;
                }
            }
        }

        private WorkerRoleDiagnostics build() {
            return new WorkerRoleDiagnostics(
                    role,
                    queued,
                    claimed,
                    running,
                    stale,
                    oldestHeartbeatAt,
                    queuedByTaskType,
                    activeByTaskType
            );
        }
    }

    public record WorkerDiagnosticsResponse(
            String status,
            long staleTimeoutSec,
            long reconcileIntervalSec,
            Map<String, WorkerRoleDiagnostics> roles
    ) {
    }

    public record WorkerRoleDiagnostics(
            String role,
            int queued,
            int claimed,
            int running,
            int stale,
            Instant oldestHeartbeatAt,
            Map<WorkerTaskType, Integer> queuedByTaskType,
            Map<WorkerTaskType, Integer> activeByTaskType
    ) {
    }
}
