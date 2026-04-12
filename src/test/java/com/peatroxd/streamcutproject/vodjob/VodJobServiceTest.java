package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateWorkerPayload;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ExportStatus;
import com.peatroxd.streamcutproject.clipcandidate.ModerationStatus;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidatePageResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowRepository;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowWorkerPayload;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.TranscriptSegmentResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerExecutionResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerTaskResponse;
import com.peatroxd.streamcutproject.vodjob.event.JobEvent;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import com.peatroxd.streamcutproject.silence.SilenceSegmentRepository;
import com.peatroxd.streamcutproject.silence.SilenceSegmentWorkerPayload;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import com.peatroxd.streamcutproject.storage.StorageService;
import com.peatroxd.streamcutproject.transcript.TranscriptSegment;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentRepository;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentWorkerPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayloadFactory;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPort;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDownloadResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerExportResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerFailureReportPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProcessingResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerTransportAck;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecution;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionProperties;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionRepository;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionStatus;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
import com.peatroxd.streamcutproject.workertask.WorkerTaskRepository;
import com.peatroxd.streamcutproject.workertask.WorkerTaskStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VodJobServiceTest {

    @TempDir
    Path tempDir;

    private final StorageProperties storageProperties = new StorageProperties();
    private final WorkerExecutionProperties workerExecutionProperties = new WorkerExecutionProperties();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private VodJobRepository vodJobRepository;

    @Mock
    private JobEventRepository jobEventRepository;

    @Mock
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Mock
    private SilenceSegmentRepository silenceSegmentRepository;

    @Mock
    private AnalysisWindowRepository analysisWindowRepository;

    @Mock
    private ClipCandidateRepository clipCandidateRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ArtifactStorageService artifactStorageService;

    @Mock
    private WorkerExecutionRepository workerExecutionRepository;

    @Mock
    private WorkerTaskRepository workerTaskRepository;

    @Mock
    private WorkerDispatchPort workerDispatchPort;

    @Mock
    private WorkerDispatchPayloadFactory workerDispatchPayloadFactory;

    private VodJobService vodJobService;

    @BeforeEach
    void setUp() {
        storageProperties.setLocalRoot(Path.of("/var/lib/streamcut"));
        workerExecutionProperties.setStaleTimeout(Duration.ofMinutes(2));
        vodJobService = new VodJobService(
                vodJobRepository,
                jobEventRepository,
                transcriptSegmentRepository,
                silenceSegmentRepository,
                analysisWindowRepository,
                clipCandidateRepository,
                storageService,
                artifactStorageService,
                storageProperties,
                workerExecutionProperties,
                workerTaskRepository,
                workerExecutionRepository,
                workerDispatchPort,
                workerDispatchPayloadFactory,
                meterRegistry
        );
        lenient().when(workerTaskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(vodJobRepository.save(any(VodJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(workerTaskRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        lenient().when(workerTaskRepository.findAllByStatusInAndLastHeartbeatAtBeforeOrderByIdAsc(any(), any()))
                .thenReturn(List.of());
        lenient().when(workerTaskRepository.findFirstByVodJobIdOrderByIdDesc(anyLong()))
                .thenReturn(java.util.Optional.empty());
        lenient().when(workerTaskRepository.findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndStatusInOrderByIdAsc(
                anyLong(), anyLong(), any(), any()
        )).thenReturn(java.util.Optional.empty());
        lenient().when(workerTaskRepository.findAllByTaskTypeAndStatusOrderByIdAsc(any(), any()))
                .thenReturn(List.of());
        lenient().when(workerTaskRepository.findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndCandidateIdAndStatusInOrderByIdAsc(
                anyLong(), anyLong(), any(), anyLong(), any()
        )).thenReturn(java.util.Optional.empty());
        lenient().when(workerExecutionRepository.findFirstByWorkerTaskIdOrderByIdDesc(anyLong()))
                .thenReturn(java.util.Optional.empty());
        lenient().when(workerExecutionRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        lenient().when(workerExecutionRepository.findFirstByVodJobIdOrderByIdDesc(anyLong()))
                .thenReturn(java.util.Optional.empty());
        lenient().when(workerExecutionRepository.save(any(WorkerExecution.class))).thenAnswer(invocation -> {
            WorkerExecution execution = invocation.getArgument(0);
            if (execution.getId() == null) {
                execution.setId(999L);
            }
            return execution;
        });
    }

    @Test
    void createUrlJobQueuesJobAndWritesEvents() {
        when(vodJobRepository.save(any())).thenAnswer(invocation -> {
            VodJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(1L);
            }
            return job;
        });

        var response = vodJobService.createUrlJob("https://example.com/video");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("QUEUED_FOR_DOWNLOAD");
        assertThat(response.sourceType()).isEqualTo("URL");
        assertThat(response.sourceUrl()).isEqualTo("https://example.com/video");
        verify(vodJobRepository, Mockito.times(2)).save(any(VodJob.class));
        verify(jobEventRepository, Mockito.times(2)).save(any(JobEvent.class));
        assertThat(meterRegistry.get("streamcut.jobs.created")
                .tag("source_type", "URL")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void createUrlJobRejectsNonHttpUrls() {
        assertThatThrownBy(() -> vodJobService.createUrlJob("ftp://example.com/video"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("url must use http or https");
    }

    @Test
    void createUrlJobRejectsMalformedUrls() {
        assertThatThrownBy(() -> vodJobService.createUrlJob("http://[broken"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("url must be a valid http or https URL");
    }

    @Test
    void createFileJobUsesFileStatusOriginalFilenameAndStoragePath() throws Exception {
        when(vodJobRepository.save(any())).thenAnswer(invocation -> {
            VodJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(2L);
            }
            return job;
        });
        when(storageService.storeSourceVideo(Mockito.eq(2L), Mockito.eq("video.mp4"), any()))
                .thenReturn(Path.of("/var/lib/streamcut/jobs/2/source/video.mp4"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "test-content".getBytes()
        );

        var response = vodJobService.createFileJob(file);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo("QUEUED_FOR_DOWNLOAD");
        assertThat(response.sourceType()).isEqualTo("FILE");
        assertThat(response.sourceUrl()).isNull();
        assertThat(response.originalFilename()).isEqualTo("video.mp4");
        verify(vodJobRepository, Mockito.times(3)).save(any(VodJob.class));
        verify(storageService).storeSourceVideo(Mockito.eq(2L), Mockito.eq("video.mp4"), any());
        verify(jobEventRepository, Mockito.times(2)).save(any(JobEvent.class));
        assertThat(meterRegistry.get("streamcut.jobs.created")
                .tag("source_type", "FILE")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void createFileJobThrowsServerErrorWhenStorageFails() throws Exception {
        when(vodJobRepository.save(any())).thenAnswer(invocation -> {
            VodJob job = invocation.getArgument(0);
            job.setId(2L);
            return job;
        });
        when(storageService.storeSourceVideo(Mockito.eq(2L), Mockito.eq("video.mp4"), any()))
                .thenThrow(new java.io.IOException("disk full"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "test-content".getBytes()
        );

        assertThatThrownBy(() -> vodJobService.createFileJob(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Failed to store uploaded file");
    }

    @Test
    void listJobsReturnsPersistedJobsInStableOrder() {
        VodJob older = buildJob(1L, "https://example.com/older", Instant.parse("2026-04-05T10:00:00Z"));
        VodJob newer = buildJob(2L, "https://example.com/newer", Instant.parse("2026-04-05T11:00:00Z"));
        when(vodJobRepository.findAll(any(Sort.class))).thenReturn(List.of(older, newer));
        when(workerExecutionRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L, 2L)))
                .thenReturn(List.of(
                        buildExecution(older, "worker-1", WorkerTaskType.DOWNLOAD, 91L),
                        buildExecution(newer, "worker-2", WorkerTaskType.ANALYZE, 92L)
                ));
        when(workerTaskRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L, 2L)))
                .thenReturn(List.of(
                        buildQueuedTask(older, WorkerTaskType.DOWNLOAD, null),
                        buildQueuedTask(newer, WorkerTaskType.ANALYZE, null)
                ));

        List<JobListItemResponse> jobs = vodJobService.listJobs();

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).id()).isEqualTo(1L);
        assertThat(jobs.get(0).sourceUrl()).isEqualTo("https://example.com/older");
        assertThat(jobs.get(0).latestExecution()).isNotNull();
        assertThat(jobs.get(0).latestExecution().id()).isEqualTo(91L);
        assertThat(jobs.get(0).latestTask()).isNotNull();
        assertThat(jobs.get(0).latestTask().taskType()).isEqualTo("DOWNLOAD");
        assertThat(jobs.get(1).id()).isEqualTo(2L);
        assertThat(jobs.get(1).sourceUrl()).isEqualTo("https://example.com/newer");
        assertThat(jobs.get(1).latestExecution()).isNotNull();
        assertThat(jobs.get(1).latestExecution().id()).isEqualTo(92L);
        assertThat(jobs.get(1).latestTask()).isNotNull();
        assertThat(jobs.get(1).latestTask().taskType()).isEqualTo("ANALYZE");
    }

    @Test
    void getJobReturnsDetailResponse() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setSourceType("URL");
        job.setOriginalFilename("video.mp4");
        job.setStartedAt(Instant.parse("2026-04-05T10:01:00Z"));
        job.setFinishedAt(Instant.parse("2026-04-05T10:05:00Z"));
        job.setErrorMessage(null);
        job.setDurationSec(120L);
        job.setLanguage("en");
        job.setStorageVideoPath("/data/video.mp4");
        job.setStorageAudioPath("/data/audio.wav");
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setProgressPercent(48);
        job.setProgressMessage("Worker is transcribing the audio");
        WorkerTask latestTask = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        latestTask.setStatus(WorkerTaskStatus.RUNNING);
        latestTask.setLastHeartbeatAt(Instant.parse("2026-04-05T10:02:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findFirstByVodJobIdOrderByIdDesc(1L))
                .thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.ANALYZE, 77L)));
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(latestTask));

        JobDetailResponse response = vodJobService.getJob(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("TRANSCRIBING");
        assertThat(response.sourceType()).isEqualTo("URL");
        assertThat(response.originalFilename()).isEqualTo("video.mp4");
        assertThat(response.storageAudioPath()).isEqualTo("/data/audio.wav");
        assertThat(response.latestExecution()).isNotNull();
        assertThat(response.latestExecution().id()).isEqualTo(77L);
        assertThat(response.latestExecution().taskType()).isEqualTo("ANALYZE");
        assertThat(response.latestTask()).isNotNull();
        assertThat(response.latestTask().taskType()).isEqualTo("ANALYZE");
    }

    @Test
    void listJobsRecomputesAggregateStatusFromLatestTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.NEW);
        WorkerTask latestTask = WorkerTask.createQueued(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.ANALYZE,
                null,
                Instant.parse("2026-04-05T10:01:00Z")
        );
        latestTask.setStatus(WorkerTaskStatus.SUCCEEDED);
        latestTask.setFinishedAt(Instant.parse("2026-04-05T10:05:00Z"));
        when(vodJobRepository.findAll(any(Sort.class))).thenReturn(List.of(job));
        when(workerExecutionRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L))).thenReturn(List.of());
        when(workerTaskRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L))).thenReturn(List.of(latestTask));

        List<JobListItemResponse> jobs = vodJobService.listJobs();

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).status()).isEqualTo("READY_FOR_REVIEW");
        assertThat(jobs.get(0).progressPercent()).isEqualTo(100);
        assertThat(jobs.get(0).progressMessage()).isEqualTo("Analysis completed");
    }

    @Test
    void listJobsPreservesReadyForReviewProgressMessage() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        job.setProgressPercent(100);
        job.setProgressMessage("Analysis completed but no non-overlapping clip candidates were found");
        WorkerTask latestTask = WorkerTask.createQueued(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.ANALYZE,
                null,
                Instant.parse("2026-04-05T10:01:00Z")
        );
        latestTask.setStatus(WorkerTaskStatus.SUCCEEDED);
        latestTask.setFinishedAt(Instant.parse("2026-04-05T10:05:00Z"));
        when(vodJobRepository.findAll(any(Sort.class))).thenReturn(List.of(job));
        when(workerExecutionRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L))).thenReturn(List.of());
        when(workerTaskRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L))).thenReturn(List.of(latestTask));

        List<JobListItemResponse> jobs = vodJobService.listJobs();

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).status()).isEqualTo("READY_FOR_REVIEW");
        assertThat(jobs.get(0).progressMessage())
                .isEqualTo("Analysis completed but no non-overlapping clip candidates were found");
    }

    @Test
    void listJobsPrefersOpenTaskOverNewerTerminalTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.NEW);
        WorkerTask runningAnalyze = WorkerTask.createQueued(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.ANALYZE,
                null,
                Instant.parse("2026-04-05T10:01:00Z")
        );
        runningAnalyze.setStatus(WorkerTaskStatus.RUNNING);
        runningAnalyze.setLastHeartbeatAt(Instant.parse("2026-04-05T10:04:00Z"));
        WorkerTask newerFailedDownload = WorkerTask.createQueued(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.DOWNLOAD,
                null,
                Instant.parse("2026-04-05T10:05:00Z")
        );
        newerFailedDownload.setStatus(WorkerTaskStatus.FAILED);
        newerFailedDownload.setFinishedAt(Instant.parse("2026-04-05T10:06:00Z"));
        when(vodJobRepository.findAll(any(Sort.class))).thenReturn(List.of(job));
        when(workerExecutionRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L))).thenReturn(List.of());
        when(workerTaskRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(List.of(1L)))
                .thenReturn(List.of(runningAnalyze, newerFailedDownload));

        List<JobListItemResponse> jobs = vodJobService.listJobs();

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).status()).isEqualTo("EXTRACTING_AUDIO");
        assertThat(jobs.get(0).latestTask()).isNotNull();
        assertThat(jobs.get(0).latestTask().status()).isEqualTo("FAILED");
    }

    @Test
    void getJobRecomputesAggregateStatusFromLatestTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.NEW);
        WorkerTask latestTask = WorkerTask.createQueued(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.EXPORT,
                7L,
                Instant.parse("2026-04-05T10:06:00Z")
        );
        latestTask.setStatus(WorkerTaskStatus.SUCCEEDED);
        latestTask.setFinishedAt(Instant.parse("2026-04-05T10:07:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findFirstByVodJobIdOrderByIdDesc(1L)).thenReturn(java.util.Optional.empty());
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(latestTask));

        JobDetailResponse response = vodJobService.getJob(1L);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.progressPercent()).isEqualTo(100);
    }

    @Test
    void getJobThrowsNotFoundForUnknownJob() {
        when(vodJobRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> vodJobService.getJob(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job not found: 99");
    }

    @Test
    void retryJobRequeuesFailedJobAndCreatesFreshDownloadTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.FAILED);
        job.setProcessingVersion(3L);
        job.setStartedAt(Instant.parse("2026-04-05T10:01:00Z"));
        job.setFinishedAt(Instant.parse("2026-04-05T10:03:00Z"));
        job.setErrorMessage("download failed");
        job.setStorageAudioPath("/var/lib/streamcut/jobs/1/audio/audio.wav");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        JobDetailResponse response = vodJobService.retryJob(1L);

        assertThat(response.status()).isEqualTo("QUEUED_FOR_DOWNLOAD");
        assertThat(response.processingVersion()).isEqualTo(4L);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED_FOR_DOWNLOAD);
        assertThat(job.getProcessingVersion()).isEqualTo(4L);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getFinishedAt()).isNull();
        assertThat(job.getErrorMessage()).isNull();
        assertThat(job.getStorageAudioPath()).isNull();
        verify(transcriptSegmentRepository).deleteAllByJobId(1L);
        verify(silenceSegmentRepository).deleteAllByJobId(1L);
        verify(analysisWindowRepository).deleteAllByJobId(1L);
        verify(clipCandidateRepository).deleteAllByJobId(1L);

        ArgumentCaptor<WorkerTask> taskCaptor = ArgumentCaptor.forClass(WorkerTask.class);
        verify(workerTaskRepository).save(taskCaptor.capture());
        WorkerTask queuedTask = taskCaptor.getValue();
        assertThat(queuedTask.getTaskType()).isEqualTo(WorkerTaskType.DOWNLOAD);
        assertThat(queuedTask.getProcessingVersion()).isEqualTo(4L);
        assertThat(queuedTask.getStatus()).isEqualTo(WorkerTaskStatus.QUEUED);

        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("JOB_RETRIED");
        assertThat(eventCaptor.getValue().getMessage()).contains("requeued it for download");
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        assertThat(meterRegistry.get("streamcut.jobs.retried").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void retryJobRejectsNonFailedStatus() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        assertThatThrownBy(() -> vodJobService.retryJob(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job cannot be retried from status: READY_FOR_REVIEW");
    }

    @Test
    void cancelJobCancelsQueuedJobAndMarksOpenTaskCanceled() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.QUEUED_FOR_PROCESSING);
        job.setCurrentWorkerId(null);
        job.setProcessingVersion(3L);
        WorkerTask queuedTask = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerTaskRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(
                1L,
                3L,
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        )).thenReturn(List.of(queuedTask));

        JobDetailResponse response = vodJobService.cancelJob(1L);

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELED);
        assertThat(job.getProcessingVersion()).isEqualTo(3L);
        assertThat(job.getLastWorkerHeartbeatAt()).isNull();
        assertThat(job.getProgressMessage()).isEqualTo("Job canceled by operator");
        assertThat(queuedTask.getStatus()).isEqualTo(WorkerTaskStatus.CANCELED);
        assertThat(queuedTask.getFailureMessage()).isEqualTo("Canceled by operator");

        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("JOB_CANCELED");
        assertThat(eventCaptor.getValue().getMessage()).contains("queued job");
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        assertThat(meterRegistry.get("streamcut.jobs.canceled").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void cancelJobRejectsActiveStatus() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.DOWNLOADING);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        assertThatThrownBy(() -> vodJobService.cancelJob(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job cannot be canceled from status: DOWNLOADING");
    }

    @Test
    void forceFailJobFailsActiveExecutionAndMarksExportCandidateFailed() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setProcessingVersion(5L);
        job.setCurrentWorkerId("worker-1");
        WorkerTask exportTask = buildQueuedTask(job, WorkerTaskType.EXPORT, 7L);
        exportTask.markRunning(Instant.parse("2026-04-05T10:02:00Z"));
        WorkerExecution execution = buildExecution(job, exportTask, "worker-1", WorkerTaskType.EXPORT, 91L, 7L);
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(clipCandidateRepository.findAllByVodJobIdAndExportStatus(1L, ExportStatus.IN_PROGRESS))
                .thenReturn(List.of(candidate));
        when(workerExecutionRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(
                1L,
                5L,
                List.of(WorkerExecutionStatus.CLAIMED, WorkerExecutionStatus.RUNNING)
        )).thenReturn(List.of(execution));
        when(workerTaskRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(
                1L,
                5L,
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        )).thenReturn(List.of(exportTask));

        JobDetailResponse response = vodJobService.forceFailJob(1L);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Operator forced failure");
        assertThat(execution.getStatus()).isEqualTo(WorkerExecutionStatus.FAILED);
        assertThat(execution.getFailureMessage()).isEqualTo("Operator forced failure");
        assertThat(exportTask.getStatus()).isEqualTo(WorkerTaskStatus.FAILED);
        assertThat(exportTask.getFailureMessage()).isEqualTo("Operator forced failure");
        assertThat(candidate.getExportStatus()).isEqualTo(ExportStatus.FAILED);

        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("JOB_FORCE_FAILED");
        assertThat(eventCaptor.getValue().getMessage()).contains("EXPORTING_CLIP");
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        assertThat(meterRegistry.get("streamcut.jobs.failed")
                .tag("reason", "operator_force")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void forceFailJobRejectsQueuedStatus() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.QUEUED_FOR_DOWNLOAD);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        assertThatThrownBy(() -> vodJobService.forceFailJob(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job cannot be force-failed from status: QUEUED_FOR_DOWNLOAD");
    }

    @Test
    void listTranscriptSegmentsReturnsPersistedSegmentsInStableOrder() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        TranscriptSegment earlier = TranscriptSegment.create(job, 1.5, 3.0, "Hello world", 2);
        earlier.setId(21L);
        TranscriptSegment later = TranscriptSegment.create(job, 3.0, 5.0, "More text", 2);
        later.setId(22L);

        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(1L)).thenReturn(List.of(earlier, later));

        List<TranscriptSegmentResponse> segments = vodJobService.listTranscriptSegments(1L);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).id()).isEqualTo(21L);
        assertThat(segments.get(0).startSec()).isEqualTo(1.5);
        assertThat(segments.get(1).id()).isEqualTo(22L);
        assertThat(segments.get(1).text()).isEqualTo("More text");
    }

    @Test
    void listTranscriptSegmentsReturnsEmptyListWhenTranscriptMissing() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(1L)).thenReturn(List.of());

        List<TranscriptSegmentResponse> segments = vodJobService.listTranscriptSegments(1L);

        assertThat(segments).isEmpty();
    }

    @Test
    void listTranscriptSegmentsThrowsNotFoundForUnknownJob() {
        when(vodJobRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> vodJobService.listTranscriptSegments(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job not found: 99");
    }

    @Test
    void listCandidatesReturnsPersistedCandidatesInStableOrder() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate higherScore = ClipCandidate.create(job, 10.0, 20.0, 0.93, "second");
        higherScore.setId(22L);
        ClipCandidate lowerScore = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        lowerScore.setId(21L);

        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(clipCandidateRepository.findAllByJobIdOrderByScoreDescStartSecAscIdAsc(1L)).thenReturn(List.of(higherScore, lowerScore));
        List<ClipCandidateResponse> candidates = vodJobService.listCandidates(1L);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).id()).isEqualTo(22L);
        assertThat(candidates.get(0).moderationStatus()).isEqualTo("PENDING");
        assertThat(candidates.get(0).exportReady()).isFalse();
        assertThat(candidates.get(1).id()).isEqualTo(21L);
    }

    @Test
    void listCandidatesMarksExportReadyOnlyWhenArtifactExists() throws Exception {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate ready = ClipCandidate.create(job, 10.0, 20.0, 0.93, "second");
        ready.setId(22L);
        Path readyArtifact = tempDir.resolve("candidate-22.mp4");
        Files.writeString(readyArtifact, "video");
        ready.setExportedClipPath(readyArtifact.toString());
        ready.setExportStatus(ExportStatus.COMPLETED);

        ClipCandidate pending = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        pending.setId(21L);
        pending.setExportedClipPath(tempDir.resolve("candidate-21.mp4").toString());

        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(clipCandidateRepository.findAllByJobIdOrderByScoreDescStartSecAscIdAsc(1L)).thenReturn(List.of(ready, pending));
        when(artifactStorageService.exists(readyArtifact.toString())).thenReturn(true);

        List<ClipCandidateResponse> candidates = vodJobService.listCandidates(1L);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).exportReady()).isTrue();
        assertThat(candidates.get(1).exportReady()).isFalse();
    }

    @Test
    void listCandidatesPageReturnsMetadataAndSortedPage() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 25.0, 38.0, 0.87, "page candidate");
        candidate.setId(77L);

        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(clipCandidateRepository.findAllByVodJobId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(candidate), org.springframework.data.domain.PageRequest.of(1, 20), 390));

        ClipCandidatePageResponse response = vodJobService.listCandidatesPage(1L, 2, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(clipCandidateRepository).findAllByVodJobId(eq(1L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("score"),
                Sort.Order.asc("startSec"),
                Sort.Order.asc("id")
        ));
        assertThat(response.pageNumber()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.totalItems()).isEqualTo(390);
        assertThat(response.totalPages()).isEqualTo(20);
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.items()).singleElement().extracting(ClipCandidateResponse::id).isEqualTo(77L);
    }

    @Test
    void listCandidatesPageClampsOutOfRangeRequestsToLastPage() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 25.0, 38.0, 0.87, "last page");
        candidate.setId(88L);

        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(clipCandidateRepository.findAllByVodJobId(eq(1L), any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(49, 20), 25),
                        new PageImpl<>(List.of(candidate), org.springframework.data.domain.PageRequest.of(1, 20), 25)
                );

        ClipCandidatePageResponse response = vodJobService.listCandidatesPage(1L, 50, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(clipCandidateRepository, Mockito.times(2)).findAllByVodJobId(eq(1L), pageableCaptor.capture());

        List<Pageable> requests = pageableCaptor.getAllValues();
        assertThat(requests.get(0).getPageNumber()).isEqualTo(49);
        assertThat(requests.get(1).getPageNumber()).isEqualTo(1);
        assertThat(response.pageNumber()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.items()).singleElement().extracting(ClipCandidateResponse::id).isEqualTo(88L);
    }

    @Test
    void listCandidatesThrowsNotFoundForUnknownJob() {
        when(vodJobRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> vodJobService.listCandidates(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job not found: 99");
    }

    @Test
    void approveCandidateUpdatesModerationStatus() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);

        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(clipCandidateRepository.save(candidate)).thenAnswer(invocation -> invocation.getArgument(0));
        ClipCandidateResponse response = vodJobService.approveCandidate(7L);

        assertThat(candidate.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(response.moderationStatus()).isEqualTo("APPROVED");
    }

    @Test
    void rejectCandidateUpdatesModerationStatus() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);

        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(clipCandidateRepository.save(candidate)).thenAnswer(invocation -> invocation.getArgument(0));
        ClipCandidateResponse response = vodJobService.rejectCandidate(7L);

        assertThat(candidate.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(response.moderationStatus()).isEqualTo("REJECTED");
    }

    @Test
    void startExportDoesNotRequireApprovedCandidate() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);

        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(storageService.resolveExportedClipPath(1L, 7L, ".mp4"))
                .thenReturn(Path.of("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
        when(clipCandidateRepository.save(candidate)).thenAnswer(invocation -> invocation.getArgument(0));
        when(vodJobRepository.save(job)).thenAnswer(invocation -> invocation.getArgument(0));

        ExportStatusResponse response = vodJobService.startExport(7L);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(candidate.getExportStatus()).isEqualTo(ExportStatus.IN_PROGRESS);
    }

    @Test
    void startExportMarksJobExportingAndReturnsArtifactPath() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setModerationStatus(ModerationStatus.APPROVED);

        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(storageService.resolveExportedClipPath(1L, 7L, ".mp4"))
                .thenReturn(Path.of("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
        when(clipCandidateRepository.save(candidate)).thenAnswer(invocation -> invocation.getArgument(0));
        when(vodJobRepository.save(job)).thenAnswer(invocation -> invocation.getArgument(0));

        ExportStatusResponse response = vodJobService.startExport(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.jobId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.artifactPath()).isEqualTo("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        assertThat(response.exportReady()).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.EXPORTING_CLIP);
        assertThat(candidate.getExportedClipPath()).isEqualTo("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        assertThat(candidate.getExportStatus()).isEqualTo(ExportStatus.IN_PROGRESS);
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void startExportReusesExistingOpenExportTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        WorkerTask existingTask = buildQueuedTask(job, WorkerTaskType.EXPORT, 7L);

        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(storageService.resolveExportedClipPath(1L, 7L, ".mp4"))
                .thenReturn(Path.of("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
        when(clipCandidateRepository.save(candidate)).thenAnswer(invocation -> invocation.getArgument(0));
        when(vodJobRepository.save(job)).thenAnswer(invocation -> invocation.getArgument(0));
        when(workerTaskRepository.findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndCandidateIdAndStatusInOrderByIdAsc(
                job.getId(),
                job.getProcessingVersion(),
                WorkerTaskType.EXPORT,
                candidate.getId(),
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        )).thenReturn(java.util.Optional.of(existingTask));

        vodJobService.startExport(7L);

        verify(workerTaskRepository, Mockito.never()).save(any(WorkerTask.class));
    }

    @Test
    void getExportStatusReturnsCurrentPlaceholderPath() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setModerationStatus(ModerationStatus.APPROVED);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        job.setStatus(JobStatus.EXPORTING_CLIP);

        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));

        ExportStatusResponse response = vodJobService.getExportStatus(7L);

        assertThat(response.status()).isEqualTo("NOT_REQUESTED");
        assertThat(response.artifactPath()).isEqualTo("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        assertThat(response.moderationStatus()).isEqualTo("APPROVED");
        assertThat(response.exportReady()).isFalse();
    }

    @Test
    void dispatchJobBuildsPayloadMarksQueuedAndRecordsEvent() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStorageVideoPath("/var/lib/streamcut/jobs/1/source/video.mp4");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        WorkerDispatchPayload payload = new WorkerDispatchPayload(
                null,
                1L,
                1L,
                "ANALYZE",
                "/var/lib/streamcut/jobs/1/source/video.mp4",
                "URL",
                "https://example.com/video",
                null,
                null,
                null,
                null
        );
        when(workerDispatchPayloadFactory.fromDownloadJob(job, null)).thenReturn(payload);

        WorkerDispatchPayload result = vodJobService.dispatchJob(1L);

        assertThat(result).isEqualTo(payload);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED_FOR_DOWNLOAD);
        assertThat(job.getUpdatedAt()).isNotNull();
        verify(workerDispatchPort).dispatch(payload);
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void dispatchJobReusesExistingOpenDownloadTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStorageVideoPath("/var/lib/streamcut/jobs/1/source/video.mp4");
        WorkerTask existingTask = buildQueuedTask(job, WorkerTaskType.DOWNLOAD, null);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerTaskRepository.findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndStatusInOrderByIdAsc(
                job.getId(),
                job.getProcessingVersion(),
                WorkerTaskType.DOWNLOAD,
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        )).thenReturn(java.util.Optional.of(existingTask));

        WorkerDispatchPayload payload = new WorkerDispatchPayload(
                null,
                1L,
                1L,
                "ANALYZE",
                "/var/lib/streamcut/jobs/1/source/video.mp4",
                "URL",
                "https://example.com/video",
                null,
                null,
                null,
                null
        );
        when(workerDispatchPayloadFactory.fromDownloadJob(job, null)).thenReturn(payload);

        vodJobService.dispatchJob(1L);

        verify(workerTaskRepository, Mockito.never()).save(any(WorkerTask.class));
    }

    @Test
    void dispatchJobThrowsWhenStorageVideoPathIsMissing() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        when(workerDispatchPayloadFactory.fromDownloadJob(job, null))
                .thenThrow(new IllegalStateException("Job 1 has no storage video path"));

        assertThatThrownBy(() -> vodJobService.dispatchJob(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no storage video path");
    }

    @Test
    void claimNextQueuedJobReturnsEmptyWhenQueueIsEmpty() {
        when(workerTaskRepository.findFirstByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.DOWNLOAD,
                WorkerTaskStatus.QUEUED
        )).thenReturn(java.util.Optional.empty());

        var result = vodJobService.claimNextQueuedJob("worker-1", "download", null);

        assertThat(result).isEmpty();
    }

    @Test
    void claimNextQueuedJobMarksJobDownloadingAndWritesClaimEvent() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.QUEUED_FOR_DOWNLOAD);
        job.setStorageVideoPath("/var/lib/streamcut/jobs/1/source/video.mp4");
        WorkerTask queuedTask = buildQueuedTask(job, WorkerTaskType.DOWNLOAD, null);
        when(workerTaskRepository.findFirstByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.DOWNLOAD,
                WorkerTaskStatus.QUEUED
        )).thenReturn(java.util.Optional.of(queuedTask));
        WorkerDispatchPayload payload = new WorkerDispatchPayload(
                91L,
                1L,
                1L,
                "ANALYZE",
                "/var/lib/streamcut/jobs/1/source/video.mp4",
                "URL",
                "https://example.com/video",
                null,
                null,
                null,
                null
        );
        when(workerDispatchPayloadFactory.fromDownloadJob(Mockito.eq(job), anyLong())).thenReturn(payload);

        var result = vodJobService.claimNextQueuedJob("worker-1", "download", null);

        assertThat(result).contains(payload);
        assertThat(job.getStatus()).isEqualTo(JobStatus.DOWNLOADING);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();
        verify(vodJobRepository).save(job);
        ArgumentCaptor<WorkerExecution> executionCaptor = ArgumentCaptor.forClass(WorkerExecution.class);
        verify(workerExecutionRepository).save(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getWhisperDevice()).isNull();
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void claimNextProcessingJobMarksJobExtractingAudioAndWritesClaimEvent() {
        storageProperties.setLocalRoot(tempDir);
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.QUEUED_FOR_PROCESSING);
        Path videoPath = tempDir.resolve("jobs/1/source/video.mp4");
        job.setStorageVideoPath(videoPath.toString());
        WorkerTask queuedTask = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        try {
            Files.createDirectories(videoPath.getParent());
            Files.writeString(videoPath, "video-bytes");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to create analyze source video fixture", ex);
        }
        when(workerTaskRepository.findFirstByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.EXPORT,
                WorkerTaskStatus.QUEUED
        )).thenReturn(java.util.Optional.empty());
        when(workerTaskRepository.findAllByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.ANALYZE,
                WorkerTaskStatus.QUEUED
        )).thenReturn(List.of(queuedTask));
        WorkerDispatchPayload payload = new WorkerDispatchPayload(
                92L,
                1L,
                1L,
                "ANALYZE",
                videoPath.toString().replace('\\', '/'),
                "URL",
                "https://example.com/video",
                null,
                null,
                null,
                null
        );
        when(workerDispatchPayloadFactory.fromAnalyzeJob(Mockito.eq(job), anyLong())).thenReturn(payload);

        var result = vodJobService.claimNextQueuedJob("processing-worker-1", "processing", "CUDA");

        assertThat(result).contains(payload);
        assertThat(job.getStatus()).isEqualTo(JobStatus.EXTRACTING_AUDIO);
        assertThat(job.getUpdatedAt()).isNotNull();
        verify(vodJobRepository).save(job);
        ArgumentCaptor<WorkerExecution> executionCaptor = ArgumentCaptor.forClass(WorkerExecution.class);
        verify(workerExecutionRepository).save(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getWhisperDevice()).isEqualTo("cuda");
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void claimNextProcessingJobSkipsMissingAnalyzeSourceAndClaimsNextReadyTask() {
        storageProperties.setLocalRoot(tempDir);
        VodJob missingJob = buildJob(1L, "https://example.com/missing", Instant.parse("2026-04-05T10:00:00Z"));
        missingJob.setStatus(JobStatus.QUEUED_FOR_PROCESSING);
        missingJob.setStorageVideoPath(tempDir.resolve("jobs/1/source/missing.mp4").toString());
        WorkerTask missingTask = buildQueuedTask(missingJob, WorkerTaskType.ANALYZE, null);
        missingTask.setId(201L);

        VodJob readyJob = buildJob(2L, "https://example.com/ready", Instant.parse("2026-04-05T10:01:00Z"));
        readyJob.setStatus(JobStatus.QUEUED_FOR_PROCESSING);
        Path readyVideoPath = tempDir.resolve("jobs/2/source/ready.mp4");
        readyJob.setStorageVideoPath(readyVideoPath.toString());
        WorkerTask readyTask = buildQueuedTask(readyJob, WorkerTaskType.ANALYZE, null);
        readyTask.setId(202L);
        try {
            Files.createDirectories(readyVideoPath.getParent());
            Files.writeString(readyVideoPath, "video-bytes");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to create ready analyze source video fixture", ex);
        }

        when(workerTaskRepository.findFirstByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.EXPORT,
                WorkerTaskStatus.QUEUED
        )).thenReturn(java.util.Optional.empty());
        when(workerTaskRepository.findAllByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.ANALYZE,
                WorkerTaskStatus.QUEUED
        )).thenReturn(List.of(missingTask, readyTask));
        WorkerDispatchPayload payload = new WorkerDispatchPayload(
                93L,
                2L,
                1L,
                "ANALYZE",
                readyVideoPath.toString().replace('\\', '/'),
                "URL",
                "https://example.com/ready",
                null,
                null,
                null,
                null
        );
        when(workerDispatchPayloadFactory.fromAnalyzeJob(Mockito.eq(readyJob), anyLong())).thenReturn(payload);

        var result = vodJobService.claimNextQueuedJob("processing-worker-1", "processing", "cuda");

        assertThat(result).contains(payload);
        assertThat(missingTask.getStatus()).isEqualTo(WorkerTaskStatus.QUEUED);
        assertThat(readyTask.getStatus()).isEqualTo(WorkerTaskStatus.CLAIMED);
        assertThat(readyJob.getStatus()).isEqualTo(JobStatus.EXTRACTING_AUDIO);
        verify(workerDispatchPayloadFactory, Mockito.never()).fromAnalyzeJob(Mockito.eq(missingJob), anyLong());
        verify(workerDispatchPayloadFactory).fromAnalyzeJob(Mockito.eq(readyJob), anyLong());
    }

    @Test
    void claimNextProcessingJobPrefersQueuedExportTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.EXPORTING_CLIP);
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setModerationStatus(ModerationStatus.APPROVED);
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);
        WorkerTask queuedTask = buildQueuedTask(job, WorkerTaskType.EXPORT, candidate.getId());
        WorkerDispatchPayload payload = new WorkerDispatchPayload(
                93L,
                1L,
                1L,
                "EXPORT",
                null,
                "URL",
                "https://example.com/video",
                7L,
                null,
                null,
                "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"
        );
        when(workerTaskRepository.findFirstByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.EXPORT,
                WorkerTaskStatus.QUEUED
        )).thenReturn(java.util.Optional.of(queuedTask));
        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(workerDispatchPayloadFactory.fromExportCandidate(Mockito.eq(candidate), anyLong())).thenReturn(payload);

        var result = vodJobService.claimNextQueuedJob("processing-worker-1", "processing", "cuda");

        assertThat(result).contains(payload);
        assertThat(job.getCurrentWorkerId()).isEqualTo("processing-worker-1");
        assertThat(job.getProgressPercent()).isEqualTo(92);
        verify(vodJobRepository).save(job);
        verify(workerExecutionRepository).save(any(WorkerExecution.class));
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void claimNextQueuedJobRequeuesStaleAnalyzeExecutionBeforePollingQueue() {
        java.util.EnumMap<WorkerTaskType, Duration> staleTimeoutOverrides = new java.util.EnumMap<>(WorkerTaskType.class);
        staleTimeoutOverrides.put(WorkerTaskType.ANALYZE, Duration.ofMinutes(2));
        workerExecutionProperties.setStaleTimeoutOverrides(staleTimeoutOverrides);
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setCurrentWorkerId("worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        task.markRunning(Instant.now().minus(Duration.ofMinutes(10)));
        WorkerExecution execution = buildExecution(job, task, "worker-1", WorkerTaskType.ANALYZE, 71L);
        execution.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(workerTaskRepository.findAllByStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(task));
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(task));
        when(workerExecutionRepository.findFirstByWorkerTaskIdOrderByIdDesc(task.getId()))
                .thenReturn(java.util.Optional.of(execution));
        when(workerTaskRepository.findFirstByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.EXPORT,
                WorkerTaskStatus.QUEUED
        )).thenReturn(java.util.Optional.empty());

        var result = vodJobService.claimNextQueuedJob("processing-worker-2", "processing", "cuda");

        assertThat(result).isEmpty();
        assertThat(execution.getStatus()).isEqualTo(WorkerExecutionStatus.FAILED);
        assertThat(task.getStatus()).isEqualTo(WorkerTaskStatus.QUEUED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED_FOR_PROCESSING);
        assertThat(job.getCurrentWorkerId()).isNull();
        assertThat(job.getProgressPercent()).isEqualTo(28);
        assertThat(job.getProgressMessage()).contains("requeued");
        assertThat(job.getErrorMessage()).contains("timed out");
        verify(vodJobRepository, Mockito.atLeastOnce()).save(job);
        verify(jobEventRepository, Mockito.atLeastOnce()).save(any(JobEvent.class));
    }

    @Test
    void claimNextQueuedJobRequeuesStaleExportExecutionBeforePollingQueue() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setCurrentWorkerId("worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.EXPORT, 7L);
        task.markRunning(Instant.now().minus(Duration.ofMinutes(10)));
        WorkerExecution execution = buildExecution(job, task, "worker-1", WorkerTaskType.EXPORT, 72L, 7L);
        execution.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(workerTaskRepository.findAllByStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(task));
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(task));
        when(workerExecutionRepository.findFirstByWorkerTaskIdOrderByIdDesc(task.getId()))
                .thenReturn(java.util.Optional.of(execution));
        when(workerTaskRepository.findFirstByTaskTypeAndStatusOrderByIdAsc(
                WorkerTaskType.EXPORT,
                WorkerTaskStatus.QUEUED
        )).thenReturn(java.util.Optional.empty());

        var result = vodJobService.claimNextQueuedJob("processing-worker-2", "processing", "cuda");

        assertThat(result).isEmpty();
        assertThat(execution.getStatus()).isEqualTo(WorkerExecutionStatus.FAILED);
        assertThat(task.getStatus()).isEqualTo(WorkerTaskStatus.QUEUED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.EXPORTING_CLIP);
        assertThat(job.getCurrentWorkerId()).isNull();
        assertThat(job.getProgressPercent()).isEqualTo(92);
        assertThat(job.getProgressMessage()).contains("requeued");
        assertThat(job.getErrorMessage()).contains("timed out");
        verify(vodJobRepository, Mockito.atLeastOnce()).save(job);
        verify(jobEventRepository, Mockito.atLeastOnce()).save(any(JobEvent.class));
    }

    @Test
    void ingestWorkerResultReplacesGeneratedDataAndMarksReadyForReview() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setCurrentWorkerId("worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        task.markRunning(Instant.parse("2026-04-05T10:01:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(51L))
                .thenReturn(java.util.Optional.of(buildExecution(job, task, "worker-1", WorkerTaskType.ANALYZE, 51L)));
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(task));
        WorkerProcessingResultPayload payload = new WorkerProcessingResultPayload(
                51L,
                1L,
                "worker-1",
                1L,
                120L,
                "en",
                "/var/lib/streamcut/jobs/1/source/video.mp4",
                "/var/lib/streamcut/jobs/1/audio/audio.wav",
                List.of(new TranscriptSegmentWorkerPayload(0.0, 2.0, "hello", 1)),
                List.of(new SilenceSegmentWorkerPayload(2.0, 3.0, 1.0)),
                List.of(new AnalysisWindowWorkerPayload(0.0, 20.0, 1.0, 0.1, 0, 0.8, 0.9)),
                List.of(new ClipCandidateWorkerPayload(5.0, 15.0, 0.95, "hello"))
        );

        WorkerTransportAck ack = vodJobService.ingestWorkerResult(payload);

        assertThat(ack.jobId()).isEqualTo(1L);
        assertThat(ack.status()).isEqualTo("READY_FOR_REVIEW");
        assertThat(job.getStatus()).isEqualTo(JobStatus.READY_FOR_REVIEW);
        assertThat(job.getDurationSec()).isEqualTo(120L);
        assertThat(job.getLanguage()).isEqualTo("en");
        assertThat(job.getProgressMessage()).isEqualTo("Analysis completed and 1 clip candidate is ready for review");
        assertThat(job.getStorageVideoPath()).isEqualTo("/var/lib/streamcut/jobs/1/source/video.mp4");
        assertThat(job.getStorageAudioPath()).isEqualTo("/var/lib/streamcut/jobs/1/audio/audio.wav");
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        verify(transcriptSegmentRepository).deleteAllByJobId(1L);
        verify(silenceSegmentRepository).deleteAllByJobId(1L);
        verify(analysisWindowRepository).deleteAllByJobId(1L);
        verify(clipCandidateRepository).deleteAllByJobId(1L);
        verify(transcriptSegmentRepository).saveAll(any());
        verify(silenceSegmentRepository).saveAll(any());
        verify(analysisWindowRepository).saveAll(any());
        verify(clipCandidateRepository).saveAll(any());
        verify(workerExecutionRepository, Mockito.atLeastOnce()).save(any(WorkerExecution.class));
        verify(jobEventRepository).save(any(JobEvent.class));
        assertThat(meterRegistry.get("streamcut.jobs.completed").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void ingestWorkerResultReportsWhenNoClipCandidatesWereFound() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setCurrentWorkerId("worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        task.markRunning(Instant.parse("2026-04-05T10:01:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(51L))
                .thenReturn(java.util.Optional.of(buildExecution(job, task, "worker-1", WorkerTaskType.ANALYZE, 51L)));
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(task));

        WorkerTransportAck ack = vodJobService.ingestWorkerResult(new WorkerProcessingResultPayload(
                51L,
                1L,
                "worker-1",
                1L,
                120L,
                "en",
                "/var/lib/streamcut/jobs/1/source/video.mp4",
                "/var/lib/streamcut/jobs/1/audio/audio.wav",
                List.of(new TranscriptSegmentWorkerPayload(0.0, 2.0, "hello", 1)),
                List.of(new SilenceSegmentWorkerPayload(2.0, 3.0, 1.0)),
                List.of(new AnalysisWindowWorkerPayload(0.0, 20.0, 1.0, 0.1, 0, 0.8, 0.9)),
                List.of()
        ));

        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);

        assertThat(ack.status()).isEqualTo("READY_FOR_REVIEW");
        assertThat(job.getStatus()).isEqualTo(JobStatus.READY_FOR_REVIEW);
        assertThat(job.getProgressMessage()).isEqualTo("Analysis completed but no non-overlapping clip candidates were found");
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        verify(jobEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getMessage())
                .isEqualTo("Worker processing completed but no non-overlapping clip candidates were found");
    }

    @Test
    void ingestWorkerDownloadResultQueuesJobForProcessing() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.DOWNLOADING);
        job.setCurrentWorkerId("download-worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.DOWNLOAD, null);
        task.markRunning(Instant.parse("2026-04-05T10:01:00Z"));
        WorkerTask latestTask = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(52L))
                .thenReturn(java.util.Optional.of(buildExecution(job, task, "download-worker-1", WorkerTaskType.DOWNLOAD, 52L)));
        WorkerTransportAck ack = vodJobService.ingestWorkerDownloadResult(
                new WorkerDownloadResultPayload(
                        52L,
                        1L,
                        "download-worker-1",
                        1L,
                        "/var/lib/streamcut/jobs/1/source/video.mp4"
                )
        );

        assertThat(ack.jobId()).isEqualTo(1L);
        assertThat(ack.status()).isEqualTo("QUEUED_FOR_PROCESSING");
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED_FOR_PROCESSING);
        assertThat(job.getStorageVideoPath()).isEqualTo("/var/lib/streamcut/jobs/1/source/video.mp4");
        assertThat(job.getCurrentWorkerId()).isNull();
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        verify(workerExecutionRepository, Mockito.atLeastOnce()).save(any(WorkerExecution.class));
        verify(vodJobRepository).save(job);
        verify(jobEventRepository, Mockito.times(2)).save(any(JobEvent.class));
    }

    @Test
    void ingestWorkerDownloadResultRejectsVideoPathOutsideStorageRoot() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.DOWNLOADING);
        job.setCurrentWorkerId("download-worker-1");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(52L))
                .thenReturn(java.util.Optional.of(buildExecution(job, "download-worker-1", WorkerTaskType.DOWNLOAD, 52L)));

        assertThatThrownBy(() -> vodJobService.ingestWorkerDownloadResult(
                new WorkerDownloadResultPayload(
                        52L,
                        1L,
                        "download-worker-1",
                        1L,
                        "/tmp/outside/video.mp4"
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("worker download video path must stay within the configured storage root");
    }

    @Test
    void ingestWorkerResultRejectsVideoPathOutsideStorageRoot() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setCurrentWorkerId("worker-1");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(53L))
                .thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.ANALYZE, 53L)));

        WorkerProcessingResultPayload payload = new WorkerProcessingResultPayload(
                53L,
                1L,
                "worker-1",
                1L,
                120L,
                "en",
                "/tmp/outside/video.mp4",
                "/var/lib/streamcut/jobs/1/audio/audio.wav",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> vodJobService.ingestWorkerResult(payload))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("worker video path must stay within the configured storage root");
    }

    @Test
    void ingestWorkerResultRejectsExecutionWithWrongTaskType() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setCurrentWorkerId("worker-1");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(53L))
                .thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.DOWNLOAD, 53L)));

        WorkerProcessingResultPayload payload = new WorkerProcessingResultPayload(
                53L,
                1L,
                "worker-1",
                1L,
                120L,
                "en",
                "/var/lib/streamcut/jobs/1/source/video.mp4",
                "/var/lib/streamcut/jobs/1/audio/audio.wav",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> vodJobService.ingestWorkerResult(payload))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("task type mismatch");
    }

    @Test
    void reportWorkerFailureMarksJobFailedAndStoresErrorMessage() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setCurrentWorkerId("worker-1");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(54L))
                .thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.ANALYZE, 54L)));

        WorkerTransportAck ack = vodJobService.reportWorkerFailure(
                new WorkerFailureReportPayload(54L, 1L, "worker-1", 1L, "TRANSCRIBING", "transcription failed")
        );

        assertThat(ack.jobId()).isEqualTo(1L);
        assertThat(ack.status()).isEqualTo("FAILED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("TRANSCRIBING: transcription failed");
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        verify(workerExecutionRepository, Mockito.atLeastOnce()).save(any(WorkerExecution.class));
        verify(vodJobRepository).save(job);
        verify(jobEventRepository).save(any(JobEvent.class));
        assertThat(meterRegistry.get("streamcut.jobs.failed")
                .tag("reason", "worker_failure")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void getExportArtifactReferenceRejectsNonCompletedExport() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        candidate.setExportStatus(ExportStatus.FAILED);
        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));

        assertThatThrownBy(() -> vodJobService.getExportArtifactReference(7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Export artifact is not ready for candidate: 7");
    }

    @Test
    void ingestWorkerExportResultMarksJobCompletedAndPersistsArtifactPath() throws Exception {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setCurrentWorkerId("worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.EXPORT, 7L);
        task.markRunning(Instant.parse("2026-04-05T10:01:00Z"));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setModerationStatus(ModerationStatus.APPROVED);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(workerExecutionRepository.findById(55L))
                .thenReturn(java.util.Optional.of(buildExecution(job, task, "worker-1", WorkerTaskType.EXPORT, 55L, 7L)));
        when(artifactStorageService.storeCompletedExport(
                Mockito.eq(1L),
                Mockito.eq(7L),
                Mockito.eq(Path.of("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"))
        )).thenReturn("s3://streamcut-artifacts/exports/jobs/1/candidate-7.mp4");

        WorkerTransportAck ack = vodJobService.ingestWorkerExportResult(
                new WorkerExportResultPayload(55L, 1L, "worker-1", 1L, 7L, "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4")
        );

        assertThat(ack.jobId()).isEqualTo(1L);
        assertThat(ack.status()).isEqualTo("COMPLETED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(candidate.getExportedClipPath()).isEqualTo("s3://streamcut-artifacts/exports/jobs/1/candidate-7.mp4");
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        verify(workerExecutionRepository, Mockito.atLeastOnce()).save(any(WorkerExecution.class));
        verify(clipCandidateRepository).save(candidate);
        verify(vodJobRepository).save(job);
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void updateWorkerProgressClearsPreviousStageProgressEvents() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.EXTRACTING_AUDIO);
        job.setCurrentWorkerId("worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        task.markRunning(Instant.parse("2026-04-05T10:01:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(56L))
                .thenReturn(java.util.Optional.of(buildExecution(job, task, "worker-1", WorkerTaskType.ANALYZE, 56L)));

        WorkerTransportAck ack = vodJobService.updateWorkerProgress(
                new com.peatroxd.streamcutproject.workerdispatch.WorkerProgressUpdatePayload(
                        56L,
                        1L,
                        "worker-1",
                        1L,
                        "TRANSCRIBING",
                        60,
                        "Worker is transcribing the audio"
                )
        );

        assertThat(ack.status()).isEqualTo("TRANSCRIBING");
        assertThat(job.getStatus()).isEqualTo(JobStatus.TRANSCRIBING);
        verify(jobEventRepository).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void updateWorkerProgressKeepsCurrentStageProgressEvents() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.DOWNLOADING);
        job.setCurrentWorkerId("worker-1");
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.DOWNLOAD, null);
        task.markRunning(Instant.parse("2026-04-05T10:01:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(57L))
                .thenReturn(java.util.Optional.of(buildExecution(job, task, "worker-1", WorkerTaskType.DOWNLOAD, 57L)));

        WorkerTransportAck ack = vodJobService.updateWorkerProgress(
                new com.peatroxd.streamcutproject.workerdispatch.WorkerProgressUpdatePayload(
                        57L,
                        1L,
                        "worker-1",
                        1L,
                        "DOWNLOADING",
                        20,
                        "Downloading source video (16%)"
                )
        );

        assertThat(ack.status()).isEqualTo("DOWNLOADING");
        assertThat(job.getStatus()).isEqualTo(JobStatus.DOWNLOADING);
        verify(jobEventRepository, Mockito.never()).deleteAllByVodJobIdAndEventType(1L, "WORKER_PROGRESS");
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void ingestWorkerExportResultRejectsArtifactPathOutsideStorageRoot() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setCurrentWorkerId("worker-1");
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setModerationStatus(ModerationStatus.APPROVED);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(workerExecutionRepository.findById(55L))
                .thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.EXPORT, 55L, 7L)));

        assertThatThrownBy(() -> vodJobService.ingestWorkerExportResult(
                new WorkerExportResultPayload(55L, 1L, "worker-1", 1L, 7L, "/tmp/outside/candidate-7.mp4")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("worker export artifact path must stay within the configured storage root");
    }

    @Test
    void ingestWorkerExportResultRejectsExecutionForDifferentCandidate() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setCurrentWorkerId("worker-1");
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setModerationStatus(ModerationStatus.APPROVED);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(workerExecutionRepository.findById(55L))
                .thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.EXPORT, 55L, 9L)));

        assertThatThrownBy(() -> vodJobService.ingestWorkerExportResult(
                new WorkerExportResultPayload(55L, 1L, "worker-1", 1L, 7L, "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to candidate");
    }

    @Test
    void updateWorkerProgressRejectsCallbackWithoutActiveExecution() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        job.setCurrentWorkerId(null);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findById(56L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> vodJobService.updateWorkerProgress(
                new com.peatroxd.streamcutproject.workerdispatch.WorkerProgressUpdatePayload(
                        56L,
                        1L,
                        "worker-1",
                        1L,
                        "TRANSCRIBING",
                        60,
                        "late callback"
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unknown execution");
    }

    @Test
    void recoverStaleExecutionsKeepsHealthyLongRunningAnalyzeTask() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        task.setId(501L);
        task.markRunning(Instant.now().minus(Duration.ofMinutes(6)));
        task.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(5)));
        when(workerTaskRepository.findAllByStatusInOrderByIdAsc(List.of(
                WorkerTaskStatus.CLAIMED,
                WorkerTaskStatus.RUNNING
        ))).thenReturn(List.of(task));

        vodJobService.recoverStaleExecutions();

        assertThat(task.getStatus()).isEqualTo(WorkerTaskStatus.RUNNING);
        assertThat(job.getStatus()).isEqualTo(JobStatus.TRANSCRIBING);
        verify(workerTaskRepository, Mockito.never()).save(any(WorkerTask.class));
        verify(workerExecutionRepository, Mockito.never()).save(any(WorkerExecution.class));
        verify(jobEventRepository, Mockito.never()).save(any(JobEvent.class));
    }

    @Test
    void recoverStaleExecutionsRequeuesAnalyzeTaskAfterAnalyzeTimeout() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        WorkerTask task = buildQueuedTask(job, WorkerTaskType.ANALYZE, null);
        task.setId(502L);
        task.markRunning(Instant.now().minus(Duration.ofMinutes(26)));
        task.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(25)));
        WorkerExecution execution = buildExecution(job, task, "worker-1", WorkerTaskType.ANALYZE, 88L);
        when(workerTaskRepository.findAllByStatusInOrderByIdAsc(List.of(
                WorkerTaskStatus.CLAIMED,
                WorkerTaskStatus.RUNNING
        ))).thenReturn(List.of(task));
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(task));
        when(workerExecutionRepository.findFirstByWorkerTaskIdOrderByIdDesc(502L))
                .thenReturn(java.util.Optional.of(execution));

        vodJobService.recoverStaleExecutions();

        assertThat(task.getStatus()).isEqualTo(WorkerTaskStatus.QUEUED);
        assertThat(task.getFailureMessage()).contains("timed out after heartbeat stall");
        assertThat(execution.getStatus()).isEqualTo(WorkerExecutionStatus.FAILED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED_FOR_PROCESSING);
        assertThat(job.getCurrentWorkerId()).isNull();
        assertThat(job.getErrorMessage()).contains("timed out after heartbeat stall");
        verify(workerTaskRepository).save(task);
        verify(workerExecutionRepository).save(execution);
        verify(vodJobRepository, Mockito.atLeastOnce()).save(job);
        verify(jobEventRepository, Mockito.times(2)).save(any(JobEvent.class));
        assertThat(meterRegistry.get("streamcut.recovery.stale.actions").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void listJobEventsReturnsPersistedEventsInStableOrder() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        JobEvent earlier = Mockito.mock(JobEvent.class);
        when(earlier.getId()).thenReturn(10L);
        when(earlier.getEventType()).thenReturn("JOB_CREATED");
        when(earlier.getMessage()).thenReturn("Job created");
        when(earlier.getCreatedAt()).thenReturn(Instant.parse("2026-04-05T10:00:01Z"));

        JobEvent later = Mockito.mock(JobEvent.class);
        when(later.getId()).thenReturn(11L);
        when(later.getEventType()).thenReturn("JOB_QUEUED_FOR_DOWNLOAD");
        when(later.getMessage()).thenReturn("Job queued for download worker");
        when(later.getCreatedAt()).thenReturn(Instant.parse("2026-04-05T10:00:02Z"));

        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(earlier, later));

        List<JobEventResponse> events = vodJobService.listJobEvents(1L);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).id()).isEqualTo(10L);
        assertThat(events.get(0).eventType()).isEqualTo("JOB_CREATED");
        assertThat(events.get(1).id()).isEqualTo(11L);
        assertThat(events.get(1).eventType()).isEqualTo("JOB_QUEUED_FOR_DOWNLOAD");
    }

    @Test
    void listWorkerExecutionsReturnsPersistedExecutionsInStableOrder() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        WorkerExecution first = buildExecution(job, "worker-1", WorkerTaskType.DOWNLOAD, 81L);
        first.setStatus(WorkerExecutionStatus.SUCCEEDED);
        WorkerExecution second = buildExecution(job, "worker-2", WorkerTaskType.ANALYZE, 82L);
        second.setStatus(WorkerExecutionStatus.RUNNING);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findAllByVodJobIdOrderByClaimedAtAscIdAsc(1L)).thenReturn(List.of(first, second));

        List<WorkerExecutionResponse> executions = vodJobService.listWorkerExecutions(1L);

        assertThat(executions).hasSize(2);
        assertThat(executions.get(0).id()).isEqualTo(81L);
        assertThat(executions.get(0).taskType()).isEqualTo("DOWNLOAD");
        assertThat(executions.get(0).status()).isEqualTo("SUCCEEDED");
        assertThat(executions.get(1).id()).isEqualTo(82L);
        assertThat(executions.get(1).taskType()).isEqualTo("ANALYZE");
        assertThat(executions.get(1).status()).isEqualTo("RUNNING");
    }

    @Test
    void listWorkerTasksReturnsPersistedTasksInStableOrder() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        WorkerTask first = WorkerTask.createQueued(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.DOWNLOAD,
                null,
                Instant.parse("2026-04-05T10:00:05Z")
        );
        first.setStatus(WorkerTaskStatus.SUCCEEDED);
        first.setClaimedAt(Instant.parse("2026-04-05T10:00:06Z"));
        first.setFinishedAt(Instant.parse("2026-04-05T10:00:10Z"));
        WorkerTask second = WorkerTask.createQueued(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.ANALYZE,
                null,
                Instant.parse("2026-04-05T10:00:15Z")
        );
        second.setStatus(WorkerTaskStatus.RUNNING);
        second.setClaimedAt(Instant.parse("2026-04-05T10:00:16Z"));
        second.setLastHeartbeatAt(Instant.parse("2026-04-05T10:00:20Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(first, second));

        List<WorkerTaskResponse> tasks = vodJobService.listWorkerTasks(1L);

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).taskType()).isEqualTo("DOWNLOAD");
        assertThat(tasks.get(0).status()).isEqualTo("SUCCEEDED");
        assertThat(tasks.get(1).taskType()).isEqualTo("ANALYZE");
        assertThat(tasks.get(1).status()).isEqualTo("RUNNING");
    }

    @Test
    void listJobEventsThrowsNotFoundForUnknownJob() {
        when(vodJobRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> vodJobService.listJobEvents(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job not found: 99");
    }

    private static VodJob buildJob(Long id, String sourceUrl, Instant createdAt) {
        VodJob job = new VodJob();
        job.setId(id);
        job.setSourceType("URL");
        job.setSourceUrl(sourceUrl);
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(createdAt);
        job.setUpdatedAt(createdAt);
        job.setProcessingVersion(1L);
        job.setCurrentWorkerId("worker-1");
        return job;
    }

    private static WorkerExecution buildExecution(VodJob job, String workerId, WorkerTaskType taskType, Long executionId) {
        return buildExecution(job, null, workerId, taskType, executionId, null);
    }

    private static WorkerExecution buildExecution(
            VodJob job,
            WorkerTask task,
            String workerId,
            WorkerTaskType taskType,
            Long executionId
    ) {
        return buildExecution(job, task, workerId, taskType, executionId, null);
    }

    private static WorkerTask buildQueuedTask(VodJob job, WorkerTaskType taskType, Long candidateId) {
        return WorkerTask.createQueued(job, job.getProcessingVersion(), taskType, candidateId, Instant.now());
    }

    private static WorkerExecution buildExecution(
            VodJob job,
            WorkerTask task,
            String workerId,
            WorkerTaskType taskType,
            Long executionId,
            Long candidateId
    ) {
        WorkerExecution execution = WorkerExecution.create(
                job,
                task,
                job.getProcessingVersion(),
                workerId,
                "processing",
                taskType,
                candidateId,
                Instant.parse("2026-04-05T10:00:30Z"),
                null
        );
        execution.setId(executionId);
        execution.setStatus(WorkerExecutionStatus.RUNNING);
        return execution;
    }

    private static WorkerExecution buildExecution(
            VodJob job,
            String workerId,
            WorkerTaskType taskType,
            Long executionId,
            Long candidateId
    ) {
        return buildExecution(job, null, workerId, taskType, executionId, candidateId);
    }
}
