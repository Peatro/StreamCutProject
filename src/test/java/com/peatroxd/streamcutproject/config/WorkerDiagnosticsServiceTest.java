package com.peatroxd.streamcutproject.config;

import com.peatroxd.streamcutproject.vodjob.VodJob;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionProperties;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
import com.peatroxd.streamcutproject.workertask.WorkerTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerDiagnosticsServiceTest {

    @Mock
    private WorkerTaskRepository workerTaskRepository;

    private final WorkerExecutionProperties workerExecutionProperties = new WorkerExecutionProperties();

    private WorkerDiagnosticsService workerDiagnosticsService;

    @BeforeEach
    void setUp() {
        workerExecutionProperties.setStaleTimeout(Duration.ofMinutes(2));
        Map<WorkerTaskType, Duration> staleTimeoutOverrides = new EnumMap<>(WorkerTaskType.class);
        staleTimeoutOverrides.put(WorkerTaskType.ANALYZE, Duration.ofMinutes(20));
        workerExecutionProperties.setStaleTimeoutOverrides(staleTimeoutOverrides);
        workerDiagnosticsService = new WorkerDiagnosticsService(workerTaskRepository, workerExecutionProperties);
    }

    @Test
    void snapshotUsesTaskTypeSpecificStaleTimeouts() {
        VodJob job = mock(VodJob.class);
        WorkerTask queuedDownload = WorkerTask.createQueued(job, 1L, WorkerTaskType.DOWNLOAD, null, Instant.now().minus(Duration.ofMinutes(10)));
        WorkerTask longRunningAnalyze = WorkerTask.createQueued(job, 1L, WorkerTaskType.ANALYZE, null, Instant.now().minus(Duration.ofMinutes(25)));
        longRunningAnalyze.markRunning(Instant.now().minus(Duration.ofMinutes(25)));
        longRunningAnalyze.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(5)));
        WorkerTask stalledExport = WorkerTask.createQueued(job, 1L, WorkerTaskType.EXPORT, 7L, Instant.now().minus(Duration.ofMinutes(10)));
        stalledExport.markRunning(Instant.now().minus(Duration.ofMinutes(10)));
        stalledExport.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(5)));

        when(workerTaskRepository.findAllByStatusInOrderByIdAsc(List.of(
                com.peatroxd.streamcutproject.workertask.WorkerTaskStatus.QUEUED,
                com.peatroxd.streamcutproject.workertask.WorkerTaskStatus.CLAIMED,
                com.peatroxd.streamcutproject.workertask.WorkerTaskStatus.RUNNING
        ))).thenReturn(List.of(queuedDownload, longRunningAnalyze, stalledExport));

        WorkerDiagnosticsService.WorkerDiagnosticsResponse snapshot = workerDiagnosticsService.snapshot();

        assertThat(snapshot.staleTimeoutSec()).isEqualTo(120);
        assertThat(snapshot.staleTimeoutsSec()).containsEntry(WorkerTaskType.DOWNLOAD, 120L);
        assertThat(snapshot.staleTimeoutsSec()).containsEntry(WorkerTaskType.ANALYZE, 1200L);
        assertThat(snapshot.staleTimeoutsSec()).containsEntry(WorkerTaskType.EXPORT, 120L);
        assertThat(snapshot.roles().get("download").queued()).isEqualTo(1);
        assertThat(snapshot.roles().get("processing").running()).isEqualTo(2);
        assertThat(snapshot.roles().get("processing").stale()).isEqualTo(1);
        assertThat(snapshot.roles().get("processing").activeByTaskType()).containsEntry(WorkerTaskType.ANALYZE, 1);
        assertThat(snapshot.roles().get("processing").activeByTaskType()).containsEntry(WorkerTaskType.EXPORT, 1);
    }
}
