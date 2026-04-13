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
        Duration staleTimeout = workerExecutionProperties.resolveStaleTimeout();
        Duration reconcileInterval = workerExecutionProperties.resolveReconcileInterval();
        Map<WorkerTaskType, Duration> staleTimeouts = workerExecutionProperties.resolveStaleTimeouts();
        Map<WorkerTaskType, Long> staleTimeoutsSec = new EnumMap<>(WorkerTaskType.class);
        staleTimeouts.forEach((taskType, timeout) -> staleTimeoutsSec.put(taskType, timeout.getSeconds()));

        List<WorkerTask> relevantTasks = workerTaskRepository.findAllByStatusInOrderByIdAsc(
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        );

        RoleDiagnosticsAccumulator download = new RoleDiagnosticsAccumulator("download");
        RoleDiagnosticsAccumulator processing = new RoleDiagnosticsAccumulator("processing");
        RoleDiagnosticsAccumulator export = new RoleDiagnosticsAccumulator("export");

        for (WorkerTask task : relevantTasks) {
            RoleDiagnosticsAccumulator accumulator = switch (roleFor(task.getTaskType())) {
                case DOWNLOAD -> download;
                case PROCESSING -> processing;
                case EXPORT -> export;
            };
            accumulator.accept(task, now, staleTimeouts.get(task.getTaskType()));
        }

        return new WorkerDiagnosticsResponse(
                "UP",
                staleTimeout.getSeconds(),
                Map.copyOf(staleTimeoutsSec),
                reconcileInterval.getSeconds(),
                Map.of(
                        "download", download.build(),
                        "processing", processing.build(),
                        "export", export.build()
                )
        );
    }

    private static WorkerRole roleFor(WorkerTaskType taskType) {
        return switch (taskType) {
            case DOWNLOAD -> WorkerRole.DOWNLOAD;
            case ANALYZE -> WorkerRole.PROCESSING;
            case EXPORT -> WorkerRole.EXPORT;
        };
    }

    private enum WorkerRole {
        DOWNLOAD,
        PROCESSING,
        EXPORT
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

        private void accept(WorkerTask task, Instant now, Duration staleTimeout) {
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
                if (lastHeartbeatAt.isBefore(now.minus(staleTimeout))) {
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
            Map<WorkerTaskType, Long> staleTimeoutsSec,
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
