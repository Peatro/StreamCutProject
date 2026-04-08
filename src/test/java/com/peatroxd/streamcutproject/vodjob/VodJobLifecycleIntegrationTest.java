package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowRepository;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowWorkerPayload;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ModerationStatus;
import com.peatroxd.streamcutproject.silence.SilenceSegmentRepository;
import com.peatroxd.streamcutproject.silence.SilenceSegmentWorkerPayload;
import com.peatroxd.streamcutproject.storage.StorageService;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentRepository;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentWorkerPayload;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import com.peatroxd.streamcutproject.workerdispatch.WorkerExportResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerFailureReportPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProcessingResultPayload;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecution;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionRepository;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamcut-lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Transactional
class VodJobLifecycleIntegrationTest {

    private static final Path STORAGE_ROOT;

    static {
        try {
            STORAGE_ROOT = Files.createTempDirectory("streamcut-lifecycle-storage");
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-root", () -> STORAGE_ROOT.toString());
    }

    @Autowired
    private VodJobService vodJobService;

    @Autowired
    private VodJobRepository vodJobRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Autowired
    private SilenceSegmentRepository silenceSegmentRepository;

    @Autowired
    private AnalysisWindowRepository analysisWindowRepository;

    @Autowired
    private ClipCandidateRepository clipCandidateRepository;

    @Autowired
    private JobEventRepository jobEventRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private WorkerExecutionRepository workerExecutionRepository;

    @Test
    void createUploadJobQueuesJobPersistsSourceAndWritesEvents() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "video.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );

        JobSummaryResponse response = vodJobService.createFileJob(file);
        VodJob job = vodJobRepository.findById(response.id()).orElseThrow();

        assertThat(response.status()).isEqualTo("QUEUED_FOR_DOWNLOAD");
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED_FOR_DOWNLOAD);
        assertThat(job.getStorageVideoPath()).isNotBlank();
        assertThat(Path.of(job.getStorageVideoPath())).exists();
        assertThat(jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(job.getId()))
                .extracting(event -> event.getEventType())
                .containsExactly("JOB_CREATED", "JOB_QUEUED_FOR_DOWNLOAD");
    }

    @Test
    void workerSuccessIngestPersistsGeneratedDataAndMarksReadyForReview() {
        VodJob job = vodJobRepository.save(newJob("https://example.com/video"));
        job.setCurrentWorkerId("worker-1");
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setStorageVideoPath(storageService.resolveSourceVideoPath(job.getId(), "video.mp4").toString());
        vodJobRepository.save(job);
        WorkerExecution processingExecution = workerExecutionRepository.save(WorkerExecution.create(
                job,
                null,
                job.getProcessingVersion(),
                "worker-1",
                "processing",
                WorkerTaskType.ANALYZE,
                null,
                Instant.parse("2026-04-05T10:00:30Z")
        ));

        WorkerProcessingResultPayload payload = new WorkerProcessingResultPayload(
                processingExecution.getId(),
                job.getId(),
                "worker-1",
                job.getProcessingVersion(),
                120L,
                "en",
                storageService.resolveSourceVideoPath(job.getId(), "video.mp4").toString(),
                storageService.resolveAudioPath(job.getId()).toString(),
                List.of(new TranscriptSegmentWorkerPayload(0.0, 2.0, "hello", 1)),
                List.of(new SilenceSegmentWorkerPayload(2.0, 3.0, 1.0)),
                List.of(new AnalysisWindowWorkerPayload(0.0, 20.0, 1.0, 0.1, 0, 0.8, 0.9)),
                List.of(new com.peatroxd.streamcutproject.clipcandidate.ClipCandidateWorkerPayload(5.0, 15.0, 0.95, "hello"))
        );

        vodJobService.ingestWorkerResult(payload);
        VodJob updatedJob = vodJobRepository.findById(job.getId()).orElseThrow();

        assertThat(updatedJob.getStatus()).isEqualTo(JobStatus.READY_FOR_REVIEW);
        assertThat(updatedJob.getStorageVideoPath()).isNotBlank();
        assertThat(updatedJob.getStorageAudioPath()).isNotBlank();
        assertThat(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(job.getId())).hasSize(1);
        assertThat(silenceSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(job.getId())).hasSize(1);
        assertThat(analysisWindowRepository.findAllByJobIdOrderByStartSecAscIdAsc(job.getId())).hasSize(1);
        assertThat(clipCandidateRepository.findAllByJobIdOrderByScoreDescStartSecAscIdAsc(job.getId())).hasSize(1);
    }

    @Test
    void workerFailureIngestMarksJobFailedAndWritesFailureEvent() {
        VodJob job = vodJobRepository.save(newJob("https://example.com/video"));
        job.setStatus(JobStatus.TRANSCRIBING);
        job.setCurrentWorkerId("worker-1");
        vodJobRepository.save(job);
        WorkerExecution processingExecution = workerExecutionRepository.save(WorkerExecution.create(
                job,
                null,
                job.getProcessingVersion(),
                "worker-1",
                "processing",
                WorkerTaskType.ANALYZE,
                null,
                Instant.parse("2026-04-05T10:00:30Z")
        ));

        vodJobService.reportWorkerFailure(
                new WorkerFailureReportPayload(processingExecution.getId(), job.getId(), "worker-1", job.getProcessingVersion(), "TRANSCRIBING", "transcription failed")
        );

        VodJob failedJob = vodJobRepository.findById(job.getId()).orElseThrow();
        assertThat(failedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).isEqualTo("TRANSCRIBING: transcription failed");
        assertThat(jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(job.getId()))
                .extracting(event -> event.getEventType())
                .contains("JOB_FAILED");
    }

    @Test
    void exportRequestAndCompletionMoveJobToCompleted() {
        VodJob job = vodJobRepository.save(newJob("https://example.com/video"));
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        job.setStorageVideoPath(storageService.resolveSourceVideoPath(job.getId(), "video.mp4").toString());
        vodJobRepository.save(job);

        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "first");
        candidate.setModerationStatus(ModerationStatus.APPROVED);
        ClipCandidate savedCandidate = clipCandidateRepository.save(candidate);

        vodJobService.startExport(savedCandidate.getId());
        VodJob exportingJob = vodJobRepository.findById(job.getId()).orElseThrow();
        assertThat(exportingJob.getStatus()).isEqualTo(JobStatus.EXPORTING_CLIP);
        exportingJob.setCurrentWorkerId("worker-1");
        vodJobRepository.save(exportingJob);
        WorkerExecution exportExecution = workerExecutionRepository.save(WorkerExecution.create(
                exportingJob,
                null,
                exportingJob.getProcessingVersion(),
                "worker-1",
                "processing",
                WorkerTaskType.EXPORT,
                savedCandidate.getId(),
                Instant.parse("2026-04-05T10:01:00Z")
        ));

        vodJobService.ingestWorkerExportResult(
                new WorkerExportResultPayload(
                        exportExecution.getId(),
                        job.getId(),
                        "worker-1",
                        job.getProcessingVersion(),
                        savedCandidate.getId(),
                        storageService.resolveExportedClipPath(job.getId(), savedCandidate.getId(), ".mp4").toString()
                )
        );

        VodJob completedJob = vodJobRepository.findById(job.getId()).orElseThrow();
        ClipCandidate completedCandidate = clipCandidateRepository.findById(savedCandidate.getId()).orElseThrow();
        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completedCandidate.getExportedClipPath()).isNotBlank();
    }

    private static VodJob newJob(String sourceUrl) {
        VodJob job = newVodJob();
        job.setSourceType("URL");
        job.setSourceUrl(sourceUrl);
        job.setStatus(JobStatus.QUEUED_FOR_DOWNLOAD);
        job.setCreatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        job.setProcessingVersion(1L);
        return job;
    }

    private static VodJob newVodJob() {
        try {
            Constructor<VodJob> constructor = VodJob.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create VodJob test fixture", ex);
        }
    }
}
