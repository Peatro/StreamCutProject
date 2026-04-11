package com.peatroxd.streamcutproject.e2e;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateWorkerPayload;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import com.peatroxd.streamcutproject.storage.StorageService;
import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJobService;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDownloadResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerFailureReportPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProcessingResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProgressUpdatePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalE2eSupportService {

    private static final String DOWNLOAD_WORKER_ID = "e2e-download-worker";
    private static final String PROCESSING_WORKER_ID = "e2e-processing-worker";
    private static final String DOWNLOAD_WORKER_ROLE = "download";
    private static final String PROCESSING_WORKER_ROLE = "processing";
    private static final String TRANSCRIBING_MESSAGE = "Synthetic transcribing state for browser E2E";
    private static final String FAILED_MESSAGE = "Synthetic operator retry fixture";

    private final VodJobService vodJobService;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void resetState() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    worker_execution,
                    worker_task,
                    clip_candidate,
                    analysis_window,
                    silence_segment,
                    transcript_segment,
                    job_event,
                    vod_job
                RESTART IDENTITY CASCADE
                """);
        deleteStorageContents();
    }

    @Transactional
    public JobDetailResponse createJob(CreateE2eJobRequest request) {
        SeedJobStatus seedStatus = SeedJobStatus.parse(request.status());
        String sourceUrl = normalizedSourceUrl(request.sourceUrl(), seedStatus);
        JobSummaryResponse createdJob = vodJobService.createUrlJob(sourceUrl);

        return switch (seedStatus) {
            case QUEUED_FOR_DOWNLOAD -> vodJobService.getJob(createdJob.id());
            case TRANSCRIBING -> transitionToTranscribing(createdJob.id());
            case FAILED -> transitionToFailed(createdJob.id());
            case READY_FOR_REVIEW -> transitionToReadyForReview(createdJob.id());
        };
    }

    private JobDetailResponse transitionToFailed(Long jobId) {
        JobDetailResponse transcribingJob = transitionToTranscribing(jobId);
        Long executionId = transcribingJob.latestExecution() != null
                ? transcribingJob.latestExecution().id()
                : null;
        if (executionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Synthetic failed fixture is missing an active execution for job: " + jobId
            );
        }
        vodJobService.reportWorkerFailure(new WorkerFailureReportPayload(
                executionId,
                jobId,
                PROCESSING_WORKER_ID,
                transcribingJob.processingVersion(),
                JobStatus.TRANSCRIBING.name(),
                FAILED_MESSAGE
        ));
        return vodJobService.getJob(jobId);
    }

    private JobDetailResponse transitionToReadyForReview(Long jobId) {
        JobDetailResponse transcribingJob = transitionToTranscribing(jobId);
        Long executionId = transcribingJob.latestExecution() != null
                ? transcribingJob.latestExecution().id()
                : null;
        if (executionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Synthetic READY_FOR_REVIEW fixture is missing an active execution for job: " + jobId
            );
        }
        String videoPath = storageService.resolveSourceVideoPath(jobId, "fixture.mp4").toString();
        List<ClipCandidateWorkerPayload> candidates = List.of(
                new ClipCandidateWorkerPayload(10.0, 40.0, 0.85, "First clip excerpt"),
                new ClipCandidateWorkerPayload(60.0, 90.0, 0.72, "Second clip excerpt")
        );
        vodJobService.ingestWorkerResult(new WorkerProcessingResultPayload(
                executionId,
                jobId,
                PROCESSING_WORKER_ID,
                transcribingJob.processingVersion(),
                120L,
                "en",
                videoPath,
                null,
                List.of(),
                List.of(),
                List.of(),
                candidates
        ));
        return vodJobService.getJob(jobId);
    }

    private JobDetailResponse transitionToTranscribing(Long jobId) {
        WorkerDispatchPayload downloadClaim = claimExpectedJob(jobId, DOWNLOAD_WORKER_ID, DOWNLOAD_WORKER_ROLE);
        Path sourceVideoPath = writeSyntheticSourceVideo(jobId);
        vodJobService.ingestWorkerDownloadResult(new WorkerDownloadResultPayload(
                downloadClaim.executionId(),
                jobId,
                DOWNLOAD_WORKER_ID,
                downloadClaim.processingVersion(),
                sourceVideoPath.toString()
        ));

        WorkerDispatchPayload processingClaim = claimExpectedJob(jobId, PROCESSING_WORKER_ID, PROCESSING_WORKER_ROLE);
        vodJobService.updateWorkerProgress(new WorkerProgressUpdatePayload(
                processingClaim.executionId(),
                jobId,
                PROCESSING_WORKER_ID,
                processingClaim.processingVersion(),
                JobStatus.TRANSCRIBING.name(),
                48,
                TRANSCRIBING_MESSAGE
        ));
        return vodJobService.getJob(jobId);
    }

    private WorkerDispatchPayload claimExpectedJob(Long jobId, String workerId, String workerRole) {
        WorkerDispatchPayload payload = vodJobService.claimNextQueuedJob(workerId, workerRole, null)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Synthetic fixture could not claim a queued job for role: " + workerRole
                ));
        if (!jobId.equals(payload.jobId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Synthetic fixture claimed job " + payload.jobId() + " while preparing job " + jobId
            );
        }
        return payload;
    }

    private Path writeSyntheticSourceVideo(Long jobId) {
        Path sourceVideoPath = storageService.resolveSourceVideoPath(jobId, "fixture.mp4");
        try {
            Files.createDirectories(sourceVideoPath.getParent());
            Files.writeString(sourceVideoPath, "streamcut-e2e-source");
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create synthetic source video for job: " + jobId,
                    ex
            );
        }
        return sourceVideoPath;
    }

    private void deleteStorageContents() {
        Path storageRoot = storageProperties.getLocalRoot().toAbsolutePath().normalize();
        try {
            if (Files.exists(storageRoot)) {
                try (var paths = Files.walk(storageRoot)) {
                    paths.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(storageRoot))
                            .forEach(this::deletePath);
                }
            }
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to reset local storage for browser E2E fixtures",
                    ex
            );
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to delete E2E fixture path: " + path,
                    ex
            );
        }
    }

    private static String normalizedSourceUrl(String sourceUrl, SeedJobStatus seedStatus) {
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            return sourceUrl;
        }
        String suffix = Instant.now().toEpochMilli() + "-" + System.nanoTime();
        return "https://example.com/e2e/" + seedStatus.name().toLowerCase() + "/" + suffix;
    }

    private enum SeedJobStatus {
        QUEUED_FOR_DOWNLOAD,
        TRANSCRIBING,
        FAILED,
        READY_FOR_REVIEW;

        private static SeedJobStatus parse(String rawValue) {
            try {
                return valueOf(String.valueOf(rawValue).trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported E2E seed status: " + rawValue,
                        ex
                );
            }
        }
    }
}
