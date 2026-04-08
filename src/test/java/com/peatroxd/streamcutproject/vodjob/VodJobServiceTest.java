package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateWorkerPayload;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ExportStatus;
import com.peatroxd.streamcutproject.clipcandidate.ModerationStatus;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowRepository;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowWorkerPayload;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.TranscriptSegmentResponse;
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
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionRepository;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionStatus;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Pageable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VodJobServiceTest {

    @TempDir
    Path tempDir;

    private final StorageProperties storageProperties = new StorageProperties();

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
    private WorkerDispatchPort workerDispatchPort;

    @Mock
    private WorkerDispatchPayloadFactory workerDispatchPayloadFactory;

    private VodJobService vodJobService;

    @BeforeEach
    void setUp() {
        storageProperties.setLocalRoot(Path.of("/var/lib/streamcut"));
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
                workerExecutionRepository,
                workerDispatchPort,
                workerDispatchPayloadFactory
        );
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

        List<JobListItemResponse> jobs = vodJobService.listJobs();

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).id()).isEqualTo(1L);
        assertThat(jobs.get(0).sourceUrl()).isEqualTo("https://example.com/older");
        assertThat(jobs.get(1).id()).isEqualTo(2L);
        assertThat(jobs.get(1).sourceUrl()).isEqualTo("https://example.com/newer");
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
        job.setProgressPercent(48);
        job.setProgressMessage("Worker is transcribing the audio");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        JobDetailResponse response = vodJobService.getJob(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.sourceType()).isEqualTo("URL");
        assertThat(response.originalFilename()).isEqualTo("video.mp4");
        assertThat(response.storageAudioPath()).isEqualTo("/data/audio.wav");
    }

    @Test
    void getJobThrowsNotFoundForUnknownJob() {
        when(vodJobRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> vodJobService.getJob(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Job not found: 99");
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
        when(workerDispatchPayloadFactory.fromDownloadJob(job)).thenReturn(payload);

        WorkerDispatchPayload result = vodJobService.dispatchJob(1L);

        assertThat(result).isEqualTo(payload);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED_FOR_DOWNLOAD);
        assertThat(job.getUpdatedAt()).isNotNull();
        verify(workerDispatchPort).dispatch(payload);
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void dispatchJobThrowsWhenStorageVideoPathIsMissing() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));

        when(workerDispatchPayloadFactory.fromDownloadJob(job))
                .thenThrow(new IllegalStateException("Job 1 has no storage video path"));

        assertThatThrownBy(() -> vodJobService.dispatchJob(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no storage video path");
    }

    @Test
    void claimNextQueuedJobReturnsEmptyWhenQueueIsEmpty() {
        when(vodJobRepository.findAllByStatusForUpdate(Mockito.eq(JobStatus.QUEUED_FOR_DOWNLOAD), any(Pageable.class)))
                .thenReturn(List.of());

        var result = vodJobService.claimNextQueuedJob("worker-1", "download");

        assertThat(result).isEmpty();
    }

    @Test
    void claimNextQueuedJobMarksJobDownloadingAndWritesClaimEvent() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.QUEUED_FOR_DOWNLOAD);
        job.setStorageVideoPath("/var/lib/streamcut/jobs/1/source/video.mp4");
        when(vodJobRepository.findAllByStatusForUpdate(Mockito.eq(JobStatus.QUEUED_FOR_DOWNLOAD), any(Pageable.class)))
                .thenReturn(List.of(job));
        WorkerDispatchPayload payload = new WorkerDispatchPayload(
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
        when(workerDispatchPayloadFactory.fromDownloadJob(job)).thenReturn(payload);

        var result = vodJobService.claimNextQueuedJob("worker-1", "download");

        assertThat(result).contains(payload);
        assertThat(job.getStatus()).isEqualTo(JobStatus.DOWNLOADING);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();
        verify(vodJobRepository).save(job);
        verify(workerExecutionRepository).save(any(WorkerExecution.class));
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void claimNextProcessingJobMarksJobExtractingAudioAndWritesClaimEvent() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.QUEUED_FOR_PROCESSING);
        job.setStorageVideoPath("/var/lib/streamcut/jobs/1/source/video.mp4");
        when(clipCandidateRepository.findPendingExportsForUpdate(Mockito.eq(ExportStatus.IN_PROGRESS), any(Pageable.class)))
                .thenReturn(List.of());
        when(vodJobRepository.findAllByStatusForUpdate(Mockito.eq(JobStatus.QUEUED_FOR_PROCESSING), any(Pageable.class)))
                .thenReturn(List.of(job));
        WorkerDispatchPayload payload = new WorkerDispatchPayload(
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
        when(workerDispatchPayloadFactory.fromAnalyzeJob(job)).thenReturn(payload);

        var result = vodJobService.claimNextQueuedJob("processing-worker-1", "processing");

        assertThat(result).contains(payload);
        assertThat(job.getStatus()).isEqualTo(JobStatus.EXTRACTING_AUDIO);
        assertThat(job.getUpdatedAt()).isNotNull();
        verify(vodJobRepository).save(job);
        verify(workerExecutionRepository).save(any(WorkerExecution.class));
        verify(jobEventRepository).save(any(JobEvent.class));
    }

    @Test
    void ingestWorkerResultReplacesGeneratedDataAndMarksReadyForReview() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setCurrentWorkerId("worker-1");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("worker-1"),
                any()
        )).thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.ANALYZE)));

        WorkerProcessingResultPayload payload = new WorkerProcessingResultPayload(
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
        assertThat(job.getStorageVideoPath()).isEqualTo("/var/lib/streamcut/jobs/1/source/video.mp4");
        assertThat(job.getStorageAudioPath()).isEqualTo("/var/lib/streamcut/jobs/1/audio/audio.wav");
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
    }

    @Test
    void ingestWorkerDownloadResultQueuesJobForProcessing() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.DOWNLOADING);
        job.setCurrentWorkerId("download-worker-1");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("download-worker-1"),
                any()
        )).thenReturn(java.util.Optional.of(buildExecution(job, "download-worker-1", WorkerTaskType.DOWNLOAD)));

        WorkerTransportAck ack = vodJobService.ingestWorkerDownloadResult(
                new WorkerDownloadResultPayload(
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
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("download-worker-1"),
                any()
        )).thenReturn(java.util.Optional.of(buildExecution(job, "download-worker-1", WorkerTaskType.DOWNLOAD)));

        assertThatThrownBy(() -> vodJobService.ingestWorkerDownloadResult(
                new WorkerDownloadResultPayload(
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
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("worker-1"),
                any()
        )).thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.ANALYZE)));

        WorkerProcessingResultPayload payload = new WorkerProcessingResultPayload(
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
    void reportWorkerFailureMarksJobFailedAndStoresErrorMessage() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setCurrentWorkerId("worker-1");
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("worker-1"),
                any()
        )).thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.ANALYZE)));

        WorkerTransportAck ack = vodJobService.reportWorkerFailure(
                new WorkerFailureReportPayload(1L, "worker-1", 1L, "TRANSCRIBING", "transcription failed")
        );

        assertThat(ack.jobId()).isEqualTo(1L);
        assertThat(ack.status()).isEqualTo("FAILED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("TRANSCRIBING: transcription failed");
        verify(workerExecutionRepository, Mockito.atLeastOnce()).save(any(WorkerExecution.class));
        verify(vodJobRepository).save(job);
        verify(jobEventRepository).save(any(JobEvent.class));
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
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setId(7L);
        candidate.setModerationStatus(ModerationStatus.APPROVED);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4");
        when(clipCandidateRepository.findById(7L)).thenReturn(java.util.Optional.of(candidate));
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("worker-1"),
                any()
        )).thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.EXPORT)));
        when(artifactStorageService.storeCompletedExport(
                Mockito.eq(1L),
                Mockito.eq(7L),
                Mockito.eq(Path.of("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"))
        )).thenReturn("s3://streamcut-artifacts/exports/jobs/1/candidate-7.mp4");

        WorkerTransportAck ack = vodJobService.ingestWorkerExportResult(
                new WorkerExportResultPayload(1L, "worker-1", 1L, 7L, "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4")
        );

        assertThat(ack.jobId()).isEqualTo(1L);
        assertThat(ack.status()).isEqualTo("COMPLETED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(candidate.getExportedClipPath()).isEqualTo("s3://streamcut-artifacts/exports/jobs/1/candidate-7.mp4");
        verify(workerExecutionRepository, Mockito.atLeastOnce()).save(any(WorkerExecution.class));
        verify(clipCandidateRepository).save(candidate);
        verify(vodJobRepository).save(job);
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
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("worker-1"),
                any()
        )).thenReturn(java.util.Optional.of(buildExecution(job, "worker-1", WorkerTaskType.EXPORT)));

        assertThatThrownBy(() -> vodJobService.ingestWorkerExportResult(
                new WorkerExportResultPayload(1L, "worker-1", 1L, 7L, "/tmp/outside/candidate-7.mp4")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("worker export artifact path must stay within the configured storage root");
    }

    @Test
    void updateWorkerProgressRejectsCallbackWithoutActiveExecution() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        job.setCurrentWorkerId(null);
        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(workerExecutionRepository.findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
                Mockito.eq(1L),
                Mockito.eq(1L),
                Mockito.eq("worker-1"),
                any()
        )).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> vodJobService.updateWorkerProgress(
                new com.peatroxd.streamcutproject.workerdispatch.WorkerProgressUpdatePayload(
                        1L,
                        "worker-1",
                        1L,
                        "TRANSCRIBING",
                        60,
                        "late callback"
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no active execution");
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

    private static WorkerExecution buildExecution(VodJob job, String workerId, WorkerTaskType taskType) {
        WorkerExecution execution = WorkerExecution.create(
                job,
                job.getProcessingVersion(),
                workerId,
                "processing",
                taskType,
                null,
                Instant.parse("2026-04-05T10:00:30Z")
        );
        execution.setStatus(WorkerExecutionStatus.RUNNING);
        return execution;
    }
}
