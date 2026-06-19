package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateWorkerPayload;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidatePersistenceMapper;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ExportStatus;
import com.peatroxd.streamcutproject.clipcandidate.ModerationStatus;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateMapper;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidatePageResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowWorkerPayload;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowRepository;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowPersistenceMapper;
import com.peatroxd.streamcutproject.silence.SilenceSegmentWorkerPayload;
import com.peatroxd.streamcutproject.silence.SilenceSegmentPersistenceMapper;
import com.peatroxd.streamcutproject.silence.SilenceSegmentRepository;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobMapper;
import com.peatroxd.streamcutproject.vodjob.api.TranscriptSegmentResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerExecutionResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerTaskResponse;
import com.peatroxd.streamcutproject.vodjob.event.JobEvent;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentWorkerPayload;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentRepository;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentPersistenceMapper;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.storage.StorageService;
import com.peatroxd.streamcutproject.storage.PathSafety;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayloadFactory;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDownloadResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerExportResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerFailureReportPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProcessingResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProgressUpdatePayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPort;
import com.peatroxd.streamcutproject.workerdispatch.WorkerTransportAck;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecution;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionRepository;
import com.peatroxd.streamcutproject.workerexecution.WorkerExecutionStatus;
import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import com.peatroxd.streamcutproject.workertask.WorkerTask;
import com.peatroxd.streamcutproject.workertask.WorkerTaskRepository;
import com.peatroxd.streamcutproject.workertask.WorkerTaskStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VodJobService {

    private static final Logger log = LoggerFactory.getLogger(VodJobService.class);
    private static final Sort CANDIDATE_SORT = Sort.by(
            Sort.Order.desc("score"),
            Sort.Order.asc("startSec"),
            Sort.Order.asc("id")
    );

    private static final String SOURCE_TYPE_URL = "URL";
    private static final String SOURCE_TYPE_FILE = "FILE";
    private static final String WORKER_ROLE_DOWNLOAD = "download";
    private static final String WORKER_ROLE_PROCESSING = "processing";
    private static final String WORKER_ROLE_EXPORT = "export";
    private static final String EVENT_JOB_CREATED = "JOB_CREATED";
    private static final String EVENT_JOB_QUEUED_FOR_DOWNLOAD = "JOB_QUEUED_FOR_DOWNLOAD";
    private static final String EVENT_JOB_QUEUED_FOR_PROCESSING = "JOB_QUEUED_FOR_PROCESSING";
    private static final String EVENT_JOB_CLAIMED = "JOB_CLAIMED";
    private static final String EVENT_JOB_CANCELED = "JOB_CANCELED";
    private static final String EVENT_JOB_RETRIED = "JOB_RETRIED";
    private static final String EVENT_JOB_FORCE_FAILED = "JOB_FORCE_FAILED";
    private static final String EVENT_WORKER_PROGRESS = "WORKER_PROGRESS";
    private static final String EVENT_WORKER_EXECUTION_STALE = "WORKER_EXECUTION_STALE";
    private static final String EVENT_DOWNLOAD_COMPLETED = "JOB_DOWNLOAD_COMPLETED";
    private static final String EVENT_JOB_READY_FOR_REVIEW = "JOB_READY_FOR_REVIEW";
    private static final String EVENT_JOB_FAILED = "JOB_FAILED";
    private static final String EVENT_EXPORT_STARTED = "EXPORT_STARTED";
    private static final String EVENT_EXPORT_COMPLETED = "EXPORT_COMPLETED";
    private static final String EVENT_JOB_COMPLETED = "JOB_COMPLETED";
    private static final String METRIC_JOBS_CREATED = "streamcut.jobs.created";
    private static final String METRIC_JOBS_COMPLETED = "streamcut.jobs.completed";
    private static final String METRIC_JOBS_FAILED = "streamcut.jobs.failed";
    private static final String METRIC_JOBS_RETRIED = "streamcut.jobs.retried";
    private static final String METRIC_JOBS_CANCELED = "streamcut.jobs.canceled";
    private static final String METRIC_STALE_RECOVERY_ACTIONS = "streamcut.recovery.stale.actions";
    private static final String FAILED_REASON_WORKER_FAILURE = "worker_failure";
    private static final String FAILED_REASON_OPERATOR_FORCE = "operator_force";
    private static final Set<JobStatus> RETRYABLE_STATUSES = EnumSet.of(
            JobStatus.FAILED
    );
    private static final Set<JobStatus> CANCELABLE_STATUSES = EnumSet.of(
            JobStatus.QUEUED_FOR_DOWNLOAD,
            JobStatus.QUEUED_FOR_PROCESSING
    );
    private static final Set<JobStatus> FORCE_FAILABLE_STATUSES = EnumSet.of(
            JobStatus.DOWNLOADING,
            JobStatus.EXTRACTING_AUDIO,
            JobStatus.TRANSCRIBING,
            JobStatus.DETECTING_SILENCE,
            JobStatus.ANALYZING_WINDOWS,
            JobStatus.GENERATING_CANDIDATES,
            JobStatus.EXPORTING_CLIP
    );
    private static final Set<JobStatus> DELETABLE_STATUSES = EnumSet.of(
            JobStatus.COMPLETED,
            JobStatus.FAILED,
            JobStatus.CANCELED,
            JobStatus.READY_FOR_REVIEW
    );

    private final VodJobRepository vodJobRepository;
    private final JobEventRepository jobEventRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final SilenceSegmentRepository silenceSegmentRepository;
    private final AnalysisWindowRepository analysisWindowRepository;
    private final ClipCandidateRepository clipCandidateRepository;
    private final StorageService storageService;
    private final ArtifactStorageService artifactStorageService;
    private final StorageProperties storageProperties;
    private final WorkerTaskOrchestrationService workerTaskOrchestrationService;
    private final WorkerTaskRepository workerTaskRepository;
    private final WorkerExecutionRepository workerExecutionRepository;
    private final WorkerDispatchPort workerDispatchPort;
    private final WorkerDispatchPayloadFactory workerDispatchPayloadFactory;
    private final MeterRegistry meterRegistry;

    @Transactional
    public JobSummaryResponse createUrlJob(String url) {
        String normalizedUrl = validateHttpUrl(url);
        VodJob savedJob = createJob(SOURCE_TYPE_URL, normalizedUrl, null);
        VodJob queuedJob = queueCreatedJob(savedJob);
        log.info("job_created jobId={} sourceType={} status={}", queuedJob.getId(), queuedJob.getSourceType(), queuedJob.getStatus());
        return JobMapper.toSummaryResponse(queuedJob);
    }

    @Transactional
    public JobSummaryResponse createFileJob(MultipartFile file) {
        Instant now = Instant.now();

        VodJob job = new VodJob();
        job.setSourceType(SOURCE_TYPE_FILE);
        job.setOriginalFilename(file.getOriginalFilename());
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setProcessingVersion(1L);

        VodJob savedJob = vodJobRepository.save(job);
        try (var inputStream = file.getInputStream()) {
            String storageVideoPath = normalizeArtifactPath(
                    storageService.storeSourceVideo(savedJob.getId(), file.getOriginalFilename(), inputStream).toString()
            );
            savedJob.setStorageVideoPath(storageVideoPath);
            persistSourceVideoReference(savedJob, Path.of(storageVideoPath), file.getOriginalFilename());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file", ex);
        }

        VodJob persistedJob = vodJobRepository.save(savedJob);
        VodJob queuedJob = queueCreatedJob(persistedJob);
        log.info(
                "job_created jobId={} sourceType={} status={} originalFilename={}",
                queuedJob.getId(),
                queuedJob.getSourceType(),
                queuedJob.getStatus(),
                queuedJob.getOriginalFilename()
        );
        return JobMapper.toSummaryResponse(queuedJob);
    }

    @Transactional(readOnly = true)
    public List<JobListItemResponse> listJobs() {
        List<VodJob> jobs = vodJobRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        Map<Long, WorkerExecution> latestExecutionByJobId = latestExecutionByJobId(jobs);
        Map<Long, List<WorkerTask>> tasksByJobId = tasksByJobId(jobs);
        return jobs
                .stream()
                .map(job -> {
                    List<WorkerTask> tasks = tasksByJobId.getOrDefault(job.getId(), List.of());
                    WorkerTask latestTask = tasks.isEmpty() ? null : tasks.get(tasks.size() - 1);
                    JobProjection.recomputeFromTasks(job, tasks);
                    return JobMapper.toListItemResponse(
                        job,
                        Optional.ofNullable(latestExecutionByJobId.get(job.getId())),
                        Optional.ofNullable(latestTask)
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getJob(Long jobId) {
        VodJob job = requireJob(jobId);
        List<WorkerTask> tasks = workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(jobId);
        Optional<WorkerTask> latestTask = tasks.isEmpty()
                ? Optional.empty()
                : Optional.of(tasks.get(tasks.size() - 1));
        JobProjection.recomputeFromTasks(job, tasks);
        return JobMapper.toDetailResponse(
                job,
                workerExecutionRepository.findFirstByVodJobIdOrderByIdDesc(jobId),
                latestTask
        );
    }

    @Transactional(readOnly = true)
    public List<TranscriptSegmentResponse> listTranscriptSegments(Long jobId) {
        requireJob(jobId);
        return transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(jobId)
                .stream()
                .map(segment -> new TranscriptSegmentResponse(
                        segment.getId(),
                        segment.getStartSec(),
                        segment.getEndSec(),
                        segment.getText(),
                        segment.getWordCount(),
                        TranscriptSegmentPersistenceMapper.wordsFromJson(segment.getWordsJson())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClipCandidateResponse> listCandidates(Long jobId) {
        requireJob(jobId);
        return clipCandidateRepository.findAllByJobIdOrderByScoreDescStartSecAscIdAsc(jobId)
                .stream()
                .map(this::toCandidateResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClipCandidatePageResponse listCandidatesPage(Long jobId, int pageNumber, int pageSize) {
        requireJob(jobId);

        Page<ClipCandidate> candidatePage = findCandidatePage(jobId, pageNumber, pageSize);
        return new ClipCandidatePageResponse(
                candidatePage.getContent().stream()
                        .map(this::toCandidateResponse)
                        .toList(),
                candidatePage.getNumber() + 1,
                candidatePage.getSize(),
                candidatePage.getTotalElements(),
                candidatePage.getTotalPages(),
                candidatePage.hasPrevious(),
                candidatePage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public List<JobEventResponse> listJobEvents(Long jobId) {
        requireJob(jobId);
        return jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(jobId)
                .stream()
                .map(event -> new JobEventResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getMessage(),
                        event.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerExecutionResponse> listWorkerExecutions(Long jobId) {
        requireJob(jobId);
        return workerExecutionRepository.findAllByVodJobIdOrderByClaimedAtAscIdAsc(jobId)
                .stream()
                .map(JobMapper::toExecutionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerTaskResponse> listWorkerTasks(Long jobId) {
        requireJob(jobId);
        return workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(jobId)
                .stream()
                .map(JobMapper::toTaskResponse)
                .toList();
    }

    @Transactional
    public JobDetailResponse retryJob(Long jobId) {
        VodJob job = requireJob(jobId);
        if (!RETRYABLE_STATUSES.contains(job.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job cannot be retried from status: " + job.getStatus()
            );
        }

        Instant now = Instant.now();
        Long previousProcessingVersion = job.getProcessingVersion();
        job.setProcessingVersion(nextProcessingVersion(job));
        cancelOpenWorkerState(job.getId(), previousProcessingVersion, now, "Retried by operator");
        clearWorkerProgressEvents(job.getId());
        resetAnalysisArtifacts(job);
        workerTaskOrchestrationService.queueDownloadJob(job, now);
        vodJobRepository.save(job);
        workerTaskRepository.save(workerTaskOrchestrationService.prepareWorkerTask(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.DOWNLOAD,
                null,
                now
        ));
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_RETRIED,
                "Operator retried the failed job and requeued it for download",
                now
        ));
        incrementCounterAfterCommit(METRIC_JOBS_RETRIED);
        log.info("job_retried jobId={} processingVersion={} status={}", job.getId(), job.getProcessingVersion(), job.getStatus());

        return toJobDetailResponse(job);
    }

    @Transactional
    public JobDetailResponse cancelJob(Long jobId) {
        VodJob job = requireJob(jobId);
        if (!CANCELABLE_STATUSES.contains(job.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job cannot be canceled from status: " + job.getStatus()
            );
        }

        Instant now = Instant.now();
        cancelOpenWorkerState(job.getId(), job.getProcessingVersion(), now, "Canceled by operator");
        clearWorkerProgressEvents(job.getId());
        JobProjection.applyCanceled(job, now, "Canceled by operator");
        job.setProgressMessage("Job canceled by operator");
        job.setLastWorkerHeartbeatAt(null);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CANCELED,
                "Operator canceled the queued job",
                now
        ));
        incrementCounterAfterCommit(METRIC_JOBS_CANCELED);
        log.warn("job_canceled jobId={} processingVersion={} status={}", job.getId(), job.getProcessingVersion(), job.getStatus());

        return toJobDetailResponse(job);
    }

    @Transactional
    public JobDetailResponse forceFailJob(Long jobId) {
        VodJob job = requireJob(jobId);
        if (!FORCE_FAILABLE_STATUSES.contains(job.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job cannot be force-failed from status: " + job.getStatus()
            );
        }

        Instant now = Instant.now();
        JobStatus forcedFromStatus = job.getStatus();
        if (forcedFromStatus == JobStatus.EXPORTING_CLIP) {
            clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.IN_PROGRESS)
                    .forEach(candidate -> {
                        candidate.setExportStatus(ExportStatus.FAILED);
                        clipCandidateRepository.save(candidate);
                    });
        }

        failOpenWorkerState(job.getId(), job.getProcessingVersion(), now, "Operator forced failure");
        clearWorkerProgressEvents(job.getId());
        JobProjection.applyFailure(job, now, "Operator forced failure");
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_FORCE_FAILED,
                "Operator force-failed the job while it was in status " + forcedFromStatus,
                now
        ));
        incrementCounterAfterCommit(METRIC_JOBS_FAILED, "reason", FAILED_REASON_OPERATOR_FORCE);
        log.warn("job_force_failed jobId={} forcedFromStatus={} processingVersion={}", job.getId(), forcedFromStatus, job.getProcessingVersion());

        return toJobDetailResponse(job);
    }

    @Transactional
    public void deleteJob(Long jobId) {
        VodJob job = requireJob(jobId);
        if (!DELETABLE_STATUSES.contains(job.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job cannot be deleted from status: " + job.getStatus()
            );
        }

        List<ClipCandidate> exportedCandidates = clipCandidateRepository.findAllByVodJobIdAndExportStatus(
                jobId, ExportStatus.COMPLETED
        );
        for (ClipCandidate candidate : exportedCandidates) {
            String reference = candidate.getExportedClipPath();
            if (reference != null && !reference.isBlank()) {
                try {
                    artifactStorageService.delete(reference);
                } catch (IOException ex) {
                    log.warn("job_delete_artifact_failed jobId={} candidateId={} message={}", jobId, candidate.getId(), ex.getMessage());
                }
            }
        }

        deleteLocalFileIfPresent(jobId, job.getStorageVideoPath(), "source video");
        deleteLocalFileIfPresent(jobId, job.getStorageAudioPath(), "audio");
        deleteArtifactReferenceIfDistinct(jobId, job.getSourceVideoReference(), job.getStorageVideoPath(), "source video");

        workerExecutionRepository.deleteAllByVodJobId(jobId);
        workerTaskRepository.deleteAllByVodJobId(jobId);
        analysisWindowRepository.deleteAllByJobId(jobId);
        silenceSegmentRepository.deleteAllByJobId(jobId);
        transcriptSegmentRepository.deleteAllByJobId(jobId);
        clipCandidateRepository.deleteAllByJobId(jobId);
        jobEventRepository.deleteAllByVodJobId(jobId);
        vodJobRepository.delete(job);
        log.info("job_deleted jobId={} status={}", jobId, job.getStatus());
    }

    @Transactional
    public JobDetailResponse completeJob(Long jobId) {
        VodJob job = requireJob(jobId);
        if (job.getStatus() != JobStatus.READY_FOR_REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job can only be completed from READY_FOR_REVIEW status, current status: " + job.getStatus()
            );
        }

        Instant now = Instant.now();
        JobProjection.applyCompletedExport(job, now);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_COMPLETED,
                "Job manually completed by operator",
                now
        ));
        incrementCounterAfterCommit(METRIC_JOBS_COMPLETED);
        log.info("job_completed jobId={} trigger=manual", job.getId());

        return toJobDetailResponse(job);
    }

    @Transactional
    public ClipCandidateResponse approveCandidate(Long candidateId) {
        return moderateCandidate(candidateId, ModerationStatus.APPROVED);
    }

    @Transactional
    public ClipCandidateResponse rejectCandidate(Long candidateId) {
        return moderateCandidate(candidateId, ModerationStatus.REJECTED);
    }

    @Transactional
    public ExportStatusResponse startExport(Long candidateId) {
        ClipCandidate candidate = requireCandidate(candidateId);
        VodJob job = candidate.getVodJob();
        if (candidate.getExportStatus() == ExportStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Export already in progress for candidate: " + candidateId
            );
        }
        if (clipCandidateRepository.existsByVodJobIdAndExportStatus(job.getId(), ExportStatus.IN_PROGRESS)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Another export is already in progress for job: " + job.getId()
            );
        }

        Instant now = Instant.now();
        String artifactPath = resolvePlannedExportArtifactPath(candidate);

        candidate.setExportedClipPath(artifactPath);
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);
        workerTaskOrchestrationService.queueTaskForRetry(job, WorkerTaskType.EXPORT, now, "Queued for clip export");
        job.setLastWorkerHeartbeatAt(null);

        clipCandidateRepository.save(candidate);
        workerTaskRepository.save(workerTaskOrchestrationService.prepareWorkerTask(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.EXPORT,
                candidate.getId(),
                now
        ));
        recomputeAndPersistJob(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_EXPORT_STARTED,
                "Export started for candidate " + candidateId,
                now
        ));
        log.info("export_started jobId={} candidateId={} artifactPath={}", job.getId(), candidateId, artifactPath);

        return toExportStatusResponse(candidate, artifactPath, exportReady(candidate));
    }

    @Transactional(readOnly = true)
    public ExportStatusResponse getExportStatus(Long exportId) {
        ClipCandidate candidate = requireCandidate(exportId);
        return toExportStatusResponse(candidate, resolveCurrentExportArtifactReference(candidate), exportReady(candidate));
    }

    @Transactional(readOnly = true)
    public String getExportArtifactReference(Long exportId) {
        ClipCandidate candidate = requireCandidate(exportId);
        if (candidate.getExportStatus() != ExportStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Export artifact is not ready for candidate: " + exportId
            );
        }
        String reference = resolveCurrentExportArtifactReference(candidate);
        if (reference == null || reference.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Export artifact not found for candidate: " + exportId
            );
        }
        if (!artifactStorageService.exists(reference)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Export artifact not found for candidate: " + exportId
            );
        }
        return reference;
    }

    @Transactional(readOnly = true)
    public String getSourceVideoReference(Long jobId) {
        VodJob job = requireJob(jobId);
        String reference = resolveSourceVideoReference(job);
        if (reference == null || reference.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Source video is not ready for job: " + jobId
            );
        }
        return reference;
    }

    @Transactional(readOnly = true)
    public Path getJobSourceVideoPath(Long jobId) {
        VodJob job = requireJob(jobId);
        String storageVideoPath = job.getStorageVideoPath();
        if (storageVideoPath == null || storageVideoPath.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Source video is not ready for job: " + jobId
            );
        }

        Path path = Path.of(storageVideoPath);
        if (!Files.exists(path)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Source video not found for job: " + jobId
            );
        }
        return path;
    }

    @Transactional
    public WorkerDispatchPayload dispatchJob(Long jobId) {
        VodJob job = vodJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));

        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromDownloadJob(job, null);
        workerDispatchPort.dispatch(payload);

        Instant now = Instant.now();
        workerTaskOrchestrationService.queueDownloadJob(job, now);
        vodJobRepository.save(job);
        workerTaskRepository.save(workerTaskOrchestrationService.prepareWorkerTask(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.DOWNLOAD,
                null,
                now
        ));

        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_QUEUED_FOR_DOWNLOAD,
                "Job queued for download worker dispatch",
                now
        ));
        log.info("job_queued jobId={} sourceType={} status={}", job.getId(), job.getSourceType(), job.getStatus());

        return payload;
    }

    @Transactional
    public Optional<WorkerDispatchPayload> claimNextQueuedJob(String workerId, String workerRole, String whisperDevice) {
        recoverStaleExecutions();
        String normalizedRole = normalizeWorkerRole(workerRole);
        String normalizedWhisperDevice = normalizeWhisperDevice(whisperDevice);
        if (WORKER_ROLE_PROCESSING.equals(normalizedRole)) {
            return claimNextAnalyzeJob(workerId, normalizedWhisperDevice);
        }
        if (WORKER_ROLE_EXPORT.equals(normalizedRole)) {
            return claimNextExportJob(workerId);
        }
        return claimNextDownloadJob(workerId);
    }

    private Optional<WorkerDispatchPayload> claimNextDownloadJob(String workerId) {
        // Download workers do not use whisper; whisperDevice is not recorded for this role.
        Instant now = Instant.now();
        Optional<WorkerTask> queuedTask = workerTaskOrchestrationService.selectNextDownloadTask(now);
        if (queuedTask.isEmpty()) {
            return Optional.empty();
        }

        WorkerTask task = queuedTask.get();
        VodJob job = task.getVodJob();
        JobProjection.applyClaimedTask(job, WorkerTaskType.DOWNLOAD, workerId, now);
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        task.markClaimed(now);
        workerTaskRepository.save(task);
        vodJobRepository.save(job);
        WorkerExecution execution = workerExecutionRepository.save(WorkerExecution.create(
                job,
                task,
                job.getProcessingVersion(),
                workerId,
                WORKER_ROLE_DOWNLOAD,
                WorkerTaskType.DOWNLOAD,
                null,
                now,
                null
        ));
        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromDownloadJob(job, execution.getId());
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CLAIMED,
                "Download claimed by worker " + workerId,
                now
        ));
        log.info("download_claimed jobId={} workerId={} status={}", job.getId(), workerId, job.getStatus());

        return Optional.of(payload);
    }

    private Optional<WorkerDispatchPayload> claimNextAnalyzeJob(String workerId, String whisperDevice) {
        Instant now = Instant.now();
        Optional<WorkerTask> queuedTask = workerTaskOrchestrationService.selectNextAnalyzeTask(now);
        if (queuedTask.isEmpty()) {
            return Optional.empty();
        }

        WorkerTask task = queuedTask.get();
        VodJob job = task.getVodJob();
        JobProjection.applyClaimedTask(job, WorkerTaskType.ANALYZE, workerId, now);
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        task.markClaimed(now);
        workerTaskRepository.save(task);
        vodJobRepository.save(job);
        WorkerExecution execution = workerExecutionRepository.save(WorkerExecution.create(
                job,
                task,
                job.getProcessingVersion(),
                workerId,
                WORKER_ROLE_PROCESSING,
                WorkerTaskType.ANALYZE,
                null,
                now,
                whisperDevice
        ));
        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromAnalyzeJob(job, execution.getId());
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CLAIMED,
                "Analysis claimed by worker " + workerId,
                now
        ));
        log.info("analysis_claimed jobId={} workerId={} status={}", job.getId(), workerId, job.getStatus());

        return Optional.of(payload);
    }

    private Optional<WorkerDispatchPayload> claimNextExportJob(String workerId) {
        Instant now = Instant.now();
        Optional<WorkerTask> queuedTask = workerTaskOrchestrationService.selectNextExportTask(now);
        if (queuedTask.isEmpty()) {
            return Optional.empty();
        }

        WorkerTask task = queuedTask.get();
        ClipCandidate candidate = clipCandidateRepository.findById(task.getCandidateId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Export candidate is missing for worker task: " + task.getId()
                ));
        VodJob job = candidate.getVodJob();
        JobProjection.applyClaimedTask(job, WorkerTaskType.EXPORT, workerId, now);
        task.markClaimed(now);
        workerTaskRepository.save(task);
        vodJobRepository.save(job);
        WorkerExecution execution = workerExecutionRepository.save(WorkerExecution.create(
                job,
                task,
                job.getProcessingVersion(),
                workerId,
                WORKER_ROLE_EXPORT,
                WorkerTaskType.EXPORT,
                candidate.getId(),
                now,
                null
        ));
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CLAIMED,
                "Export claimed by worker " + workerId + " for candidate " + candidate.getId(),
                now
        ));
        log.info(
                "export_claimed jobId={} candidateId={} workerId={} status={}",
                job.getId(),
                candidate.getId(),
                workerId,
                job.getStatus()
        );
        return Optional.of(workerDispatchPayloadFactory.fromExportCandidate(candidate, execution.getId()));
    }

    private String normalizeWhisperDevice(String whisperDevice) {
        if (whisperDevice == null) {
            return null;
        }
        String normalized = whisperDevice.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    @Transactional
    public WorkerTransportAck updateWorkerProgress(WorkerProgressUpdatePayload payload) {
        VodJob job = requireJob(payload.jobId());
        JobStatus stageStatus = parseJobStatus(payload.status(), "worker progress status");
        WorkerExecution execution = requireActiveExecution(
                job,
                payload.executionId(),
                payload.workerId(),
                payload.processingVersion(),
                expectedTaskTypeForStage(stageStatus),
                null
        );
        if (job.getStatus() != stageStatus) {
            clearWorkerProgressEvents(job.getId());
        }
        Instant now = Instant.now();
        execution.setStatus(WorkerExecutionStatus.RUNNING);
        execution.setLastHeartbeatAt(now);
        updateExecutionTaskAsRunning(execution, now);
        workerExecutionRepository.save(execution);
        JobProjection.applyProgress(job, stageStatus, now, payload.progressPercent(), payload.message());
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_WORKER_PROGRESS,
                "Worker " + payload.workerId() + " entered " + stageStatus.name() + ": " + payload.message(),
                now
        ));
        log.info(
                "worker_progress jobId={} workerId={} processingVersion={} status={} progressPercent={} message={}",
                job.getId(),
                payload.workerId(),
                payload.processingVersion(),
                stageStatus,
                payload.progressPercent(),
                payload.message()
        );

        return new WorkerTransportAck(job.getId(), stageStatus.name());
    }

    @Transactional
    public WorkerTransportAck ingestWorkerResult(WorkerProcessingResultPayload payload) {
        VodJob job = requireJob(payload.jobId());
        WorkerExecution execution = requireActiveExecution(
                job,
                payload.executionId(),
                payload.workerId(),
                payload.processingVersion(),
                WorkerTaskType.ANALYZE,
                null
        );
        List<TranscriptSegmentWorkerPayload> transcriptSegments = safeList(payload.transcriptSegments());
        List<SilenceSegmentWorkerPayload> silenceSegments = safeList(payload.silenceSegments());
        List<AnalysisWindowWorkerPayload> analysisWindows = safeList(payload.analysisWindows());
        List<ClipCandidateWorkerPayload> clipCandidates = safeList(payload.clipCandidates());

        transcriptSegmentRepository.deleteAllByJobId(job.getId());
        silenceSegmentRepository.deleteAllByJobId(job.getId());
        analysisWindowRepository.deleteAllByJobId(job.getId());
        clipCandidateRepository.deleteAllByJobId(job.getId());

        transcriptSegmentRepository.saveAll(TranscriptSegmentPersistenceMapper.toEntities(
                job,
                transcriptSegments
        ));
        silenceSegmentRepository.saveAll(SilenceSegmentPersistenceMapper.toEntities(
                job,
                silenceSegments
        ));
        analysisWindowRepository.saveAll(AnalysisWindowPersistenceMapper.toEntities(
                job,
                analysisWindows
        ));
        clipCandidateRepository.saveAll(ClipCandidatePersistenceMapper.toEntities(
                job,
                clipCandidates
        ));

        Instant now = Instant.now();
        job.setDurationSec(payload.durationSec());
        job.setLanguage(payload.language());
        if (payload.videoPath() != null && !payload.videoPath().isBlank()) {
            job.setStorageVideoPath(normalizeWorkerPath(payload.videoPath(), "worker video path"));
        }
        if (payload.audioPath() != null && !payload.audioPath().isBlank()) {
            job.setStorageAudioPath(normalizeWorkerPath(payload.audioPath(), "worker audio path"));
        }
        execution.setStatus(WorkerExecutionStatus.SUCCEEDED);
        execution.setLastHeartbeatAt(now);
        execution.setFinishedAt(now);
        completeExecutionTask(execution, now);
        workerExecutionRepository.save(execution);
        clearWorkerProgressEvents(job.getId());
        recomputeAndPersistJob(job);
        job.setProgressMessage(readyForReviewProgressMessage(clipCandidates.size()));
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_READY_FOR_REVIEW,
                readyForReviewEventMessage(clipCandidates.size()),
                now
        ));
        incrementCounterAfterCommit(METRIC_JOBS_COMPLETED);
        log.info(
                "job_result_ingested jobId={} status={} transcriptSegments={} silenceSegments={} analysisWindows={} clipCandidates={}",
                job.getId(),
                job.getStatus(),
                transcriptSegments.size(),
                silenceSegments.size(),
                analysisWindows.size(),
                clipCandidates.size()
        );

        return new WorkerTransportAck(job.getId(), JobStatus.READY_FOR_REVIEW.name());
    }

    @Transactional
    public WorkerTransportAck ingestWorkerDownloadResult(WorkerDownloadResultPayload payload) {
        VodJob job = requireJob(payload.jobId());
        WorkerExecution execution = requireActiveExecution(
                job,
                payload.executionId(),
                payload.workerId(),
                payload.processingVersion(),
                WorkerTaskType.DOWNLOAD,
                null
        );

        Instant now = Instant.now();
        job.setStorageVideoPath(normalizeWorkerPath(payload.videoPath(), "worker download video path"));
        persistSourceVideoReference(job, Path.of(job.getStorageVideoPath()), job.getOriginalFilename());
        workerTaskOrchestrationService.queueTaskForRetry(job, WorkerTaskType.ANALYZE, now, "Source video is ready and queued for processing");
        job.setErrorMessage(null);
        execution.setStatus(WorkerExecutionStatus.SUCCEEDED);
        execution.setLastHeartbeatAt(now);
        execution.setFinishedAt(now);
        completeExecutionTask(execution, now);
        workerExecutionRepository.save(execution);
        clearWorkerProgressEvents(job.getId());
        workerTaskRepository.save(workerTaskOrchestrationService.prepareWorkerTask(
                job,
                job.getProcessingVersion(),
                WorkerTaskType.ANALYZE,
                null,
                now
        ));
        recomputeAndPersistJob(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_DOWNLOAD_COMPLETED,
                "Download completed by worker " + payload.workerId(),
                now
        ));
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_QUEUED_FOR_PROCESSING,
                "Job queued for processing worker",
                now
        ));
        log.info(
                "download_result_ingested jobId={} workerId={} processingVersion={} status={} videoPath={}",
                job.getId(),
                payload.workerId(),
                payload.processingVersion(),
                job.getStatus(),
                job.getStorageVideoPath()
        );

        return new WorkerTransportAck(job.getId(), JobStatus.QUEUED_FOR_PROCESSING.name());
    }

    @Transactional
    public WorkerTransportAck ingestWorkerExportResult(WorkerExportResultPayload payload) {
        ClipCandidate candidate = requireCandidate(payload.candidateId());
        VodJob job = candidate.getVodJob();
        WorkerExecution execution = requireActiveExecution(
                job,
                payload.executionId(),
                payload.workerId(),
                payload.processingVersion(),
                WorkerTaskType.EXPORT,
                payload.candidateId()
        );
        Instant now = Instant.now();

        if (payload.artifactPath() != null && !payload.artifactPath().isBlank()) {
            try {
                Path sourceArtifactPath = PathSafety.requireWithinRoot(
                        storageProperties.getLocalRoot(),
                        Path.of(payload.artifactPath()),
                        "worker export artifact path"
                );
                String storedReference = artifactStorageService.storeCompletedExport(
                        job.getId(),
                        candidate.getId(),
                        sourceArtifactPath
                );
                candidate.setExportedClipPath(storedReference);
            } catch (IOException ex) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to persist export artifact",
                        ex
                );
            }
        }
        candidate.setExportStatus(ExportStatus.COMPLETED);
        JobProjection.applyReadyForReview(job, now);
        execution.setStatus(WorkerExecutionStatus.SUCCEEDED);
        execution.setLastHeartbeatAt(now);
        execution.setFinishedAt(now);
        completeExecutionTask(execution, now);
        workerExecutionRepository.save(execution);
        clearWorkerProgressEvents(job.getId());
        clipCandidateRepository.save(candidate);
        recomputeAndPersistJob(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_EXPORT_COMPLETED,
                "Export completed for candidate " + candidate.getId(),
                now
        ));
        tryAutoCompleteJob(job);
        log.info(
                "export_completed jobId={} candidateId={} status={} artifactPath={}",
                job.getId(),
                candidate.getId(),
                job.getStatus(),
                candidate.getExportedClipPath()
        );

        return new WorkerTransportAck(job.getId(), job.getStatus().name());
    }

    @Transactional
    public WorkerTransportAck reportWorkerFailure(WorkerFailureReportPayload payload) {
        VodJob job = requireJob(payload.jobId());
        WorkerExecution execution = requireActiveExecution(
                job,
                payload.executionId(),
                payload.workerId(),
                payload.processingVersion(),
                expectedTaskTypeForFailureState(payload.failedState()),
                null
        );
        Instant now = Instant.now();
        String failureSummary = payload.failedState() + ": " + payload.message();
        WorkerTask task = Objects.requireNonNull(
                execution.getWorkerTask(),
                "Worker callback execution is missing task linkage"
        );

        execution.setStatus(WorkerExecutionStatus.FAILED);
        execution.setLastHeartbeatAt(now);
        execution.setFinishedAt(now);
        execution.setFailureMessage(failureSummary);
        workerExecutionRepository.save(execution);

        boolean retryScheduled = workerTaskOrchestrationService.applyTaskFailurePolicy(task, now, failureSummary);
        workerTaskRepository.save(task);
        clearWorkerProgressEvents(job.getId());

        if (retryScheduled) {
            workerTaskOrchestrationService.queueTaskForRetry(
                    job,
                    task.getTaskType(),
                    now,
                    workerTaskOrchestrationService.retryScheduledProgressMessage(task)
            );
            vodJobRepository.save(job);
            log.warn(
                    "job_retry_scheduled jobId={} taskId={} taskType={} availableAt={} attempts={}/{}",
                    job.getId(),
                    task.getId(),
                    task.getTaskType(),
                    task.getAvailableAt(),
                    task.getAttemptCountOrZero(),
                    task.getMaxAttempts()
            );
            return new WorkerTransportAck(job.getId(), job.getStatus().name());
        }

        if (task.getTaskType() == WorkerTaskType.EXPORT) {
            clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.IN_PROGRESS)
                    .forEach(candidate -> {
                        candidate.setExportStatus(ExportStatus.FAILED);
                        clipCandidateRepository.save(candidate);
                    });
        }

        String deadLetterMessage = workerTaskOrchestrationService.deadLetterMessage(task, failureSummary);
        JobProjection.applyFailure(job, now, deadLetterMessage);
        job.setErrorMessage(deadLetterMessage);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_FAILED,
                "Worker reported failure in state " + payload.failedState() + ": " + deadLetterMessage,
                now
        ));
        incrementCounterAfterCommit(METRIC_JOBS_FAILED, "reason", FAILED_REASON_WORKER_FAILURE);
        log.warn(
                "job_failed jobId={} failedState={} status={} message={} deadLettered=true",
                job.getId(),
                payload.failedState(),
                job.getStatus(),
                deadLetterMessage
        );

        return new WorkerTransportAck(job.getId(), JobStatus.FAILED.name());
    }

    private WorkerExecution requireActiveExecution(
            VodJob job,
            Long executionId,
            String workerId,
            Long processingVersion,
            WorkerTaskType expectedTaskType,
            Long expectedCandidateId
    ) {
        Long currentVersion = job.getProcessingVersion();
        if (currentVersion == null || !currentVersion.equals(processingVersion)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback is stale for job: " + job.getId()
            );
        }
        String currentWorkerId = job.getCurrentWorkerId();
        if (currentWorkerId != null && !currentWorkerId.equals(workerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback does not own the active lease for job: " + job.getId()
            );
        }
        WorkerExecution execution = workerExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Worker callback references unknown execution: " + executionId
                ));
        if (!execution.getVodJob().getId().equals(job.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback execution does not belong to job: " + job.getId()
            );
        }
        if (!execution.getProcessingVersion().equals(processingVersion)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback execution version is stale for job: " + job.getId()
            );
        }
        if (!execution.getWorkerId().equals(workerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback execution does not belong to worker: " + workerId
            );
        }
        if (expectedTaskType != null && execution.getTaskType() != expectedTaskType) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback execution task type mismatch for job: " + job.getId()
            );
        }
        if (expectedCandidateId != null && !Objects.equals(execution.getCandidateId(), expectedCandidateId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback execution does not belong to candidate: " + expectedCandidateId
            );
        }
        if (!List.of(WorkerExecutionStatus.CLAIMED, WorkerExecutionStatus.RUNNING).contains(execution.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Worker callback has no active execution for job: " + job.getId()
            );
        }
        return execution;
    }

    private static WorkerTaskType expectedTaskTypeForStage(JobStatus stageStatus) {
        return switch (stageStatus) {
            case DOWNLOADING -> WorkerTaskType.DOWNLOAD;
            case EXPORTING_CLIP -> WorkerTaskType.EXPORT;
            case EXTRACTING_AUDIO, TRANSCRIBING, DETECTING_SILENCE, ANALYZING_WINDOWS, GENERATING_CANDIDATES ->
                    WorkerTaskType.ANALYZE;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported worker progress status: " + stageStatus
            );
        };
    }

    private static WorkerTaskType expectedTaskTypeForFailureState(String failedState) {
        String normalizedState = String.valueOf(failedState).trim().toUpperCase();
        if ("WORKER_INTERNAL".equals(normalizedState)) {
            return WorkerTaskType.ANALYZE;
        }
        JobStatus stageStatus = parseJobStatus(failedState, "worker failure state");
        return switch (stageStatus) {
            case DOWNLOADING -> WorkerTaskType.DOWNLOAD;
            case EXPORTING_CLIP -> WorkerTaskType.EXPORT;
            case EXTRACTING_AUDIO, TRANSCRIBING, DETECTING_SILENCE, ANALYZING_WINDOWS, GENERATING_CANDIDATES ->
                    WorkerTaskType.ANALYZE;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported worker failure state: " + stageStatus
            );
        };
    }

    private static JobStatus parseJobStatus(String rawStatus, String description) {
        try {
            return JobStatus.valueOf(String.valueOf(rawStatus).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, description + " is invalid", ex);
        }
    }

    private void resetAnalysisArtifacts(VodJob job) {
        transcriptSegmentRepository.deleteAllByJobId(job.getId());
        silenceSegmentRepository.deleteAllByJobId(job.getId());
        analysisWindowRepository.deleteAllByJobId(job.getId());
        clipCandidateRepository.deleteAllByJobId(job.getId());
        job.setStartedAt(null);
        job.setStorageAudioPath(null);
    }

    private static long nextProcessingVersion(VodJob job) {
        return job.getProcessingVersion() == null ? 1L : job.getProcessingVersion() + 1L;
    }

    private void cancelOpenWorkerState(Long jobId, Long processingVersion, Instant now, String reason) {
        workerExecutionRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(
                jobId,
                processingVersion,
                List.of(WorkerExecutionStatus.CLAIMED, WorkerExecutionStatus.RUNNING)
        ).forEach(execution -> {
            execution.setStatus(WorkerExecutionStatus.CANCELED);
            execution.setLastHeartbeatAt(now);
            execution.setFinishedAt(now);
            execution.setFailureMessage(reason);
            workerExecutionRepository.save(execution);
        });
        workerTaskRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(
                jobId,
                processingVersion,
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        ).forEach(task -> {
            task.markCanceled(now, reason);
            workerTaskRepository.save(task);
        });
    }

    private void failOpenWorkerState(Long jobId, Long processingVersion, Instant now, String reason) {
        workerExecutionRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(
                jobId,
                processingVersion,
                List.of(WorkerExecutionStatus.CLAIMED, WorkerExecutionStatus.RUNNING)
        ).forEach(execution -> {
            execution.setStatus(WorkerExecutionStatus.FAILED);
            execution.setLastHeartbeatAt(now);
            execution.setFinishedAt(now);
            execution.setFailureMessage(reason);
            workerExecutionRepository.save(execution);
        });
        workerTaskRepository.findAllByVodJobIdAndProcessingVersionAndStatusIn(
                jobId,
                processingVersion,
                List.of(WorkerTaskStatus.QUEUED, WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        ).forEach(task -> {
            task.markFailed(now, reason);
            workerTaskRepository.save(task);
        });
    }

    @Transactional
    public int recoverStaleExecutions() {
        int recoveredCount = recoverStaleExecutions(Instant.now());
        if (recoveredCount > 0) {
            incrementCounterAfterCommit(METRIC_STALE_RECOVERY_ACTIONS, recoveredCount);
        }
        return recoveredCount;
    }

    private int recoverStaleExecutions(Instant now) {
        int recoveredCount = 0;
        for (WorkerTask task : workerTaskRepository.findAllByStatusInOrderByIdAsc(
                List.of(WorkerTaskStatus.CLAIMED, WorkerTaskStatus.RUNNING)
        )) {
            if (!workerTaskOrchestrationService.isTaskStale(task, now)) {
                continue;
            }
            recoverStaleTask(task, now);
            recoveredCount++;
        }
        return recoveredCount;
    }

    private void recoverStaleTask(WorkerTask task, Instant now) {
        VodJob job = task.getVodJob();
        Optional<WorkerExecution> latestExecution = workerExecutionRepository.findFirstByWorkerTaskIdOrderByIdDesc(task.getId());
        String recoverySubject = latestExecution.map(execution -> "Worker execution " + execution.getId())
                .orElse("Worker task " + task.getId());
        String recoveryMessage = recoverySubject + " timed out after heartbeat stall";

        latestExecution.ifPresent(execution -> {
            execution.setStatus(WorkerExecutionStatus.FAILED);
            execution.setLastHeartbeatAt(now);
            execution.setFinishedAt(now);
            execution.setFailureMessage(recoveryMessage);
            workerExecutionRepository.save(execution);
        });
        boolean retryScheduled = workerTaskOrchestrationService.applyTaskFailurePolicy(task, now, recoveryMessage);
        workerTaskRepository.save(task);
        clearWorkerProgressEvents(job.getId());

        if (retryScheduled) {
            workerTaskOrchestrationService.queueTaskForRetry(
                    job,
                    task.getTaskType(),
                    now,
                    workerTaskOrchestrationService.retryScheduledProgressMessage(task)
            );
            vodJobRepository.save(job);
            jobEventRepository.save(JobEvent.create(
                    job,
                    EVENT_WORKER_EXECUTION_STALE,
                    recoveryMessage,
                    now
            ));
            log.warn(
                    "worker_execution_retry_scheduled taskId={} executionId={} jobId={} taskType={} availableAt={} attempts={}/{}",
                    task.getId(),
                    latestExecution.map(WorkerExecution::getId).orElse(null),
                    job.getId(),
                    task.getTaskType(),
                    task.getAvailableAt(),
                    task.getAttemptCountOrZero(),
                    task.getMaxAttempts()
            );
            return;
        }

        if (task.getTaskType() == WorkerTaskType.EXPORT) {
            clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.IN_PROGRESS)
                    .forEach(candidate -> {
                        candidate.setExportStatus(ExportStatus.FAILED);
                        clipCandidateRepository.save(candidate);
                    });
        }

        String deadLetterMessage = workerTaskOrchestrationService.deadLetterMessage(task, recoveryMessage);
        JobProjection.applyFailure(job, now, deadLetterMessage);
        job.setErrorMessage(deadLetterMessage);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_WORKER_EXECUTION_STALE,
                recoveryMessage,
                now
        ));
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_FAILED,
                "Stale worker execution exhausted retry budget: " + deadLetterMessage,
                now
        ));
        log.warn(
                "worker_execution_stale taskId={} executionId={} jobId={} taskType={} deadLettered=true",
                task.getId(),
                latestExecution.map(WorkerExecution::getId).orElse(null),
                job.getId(),
                task.getTaskType()
        );
    }

    private Map<Long, WorkerExecution> latestExecutionByJobId(List<VodJob> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }

        List<Long> jobIds = jobs.stream()
                .map(VodJob::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, WorkerExecution> latestExecutionByJobId = new LinkedHashMap<>();
        workerExecutionRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(jobIds)
                .forEach(execution -> latestExecutionByJobId.put(execution.getVodJob().getId(), execution));
        return latestExecutionByJobId;
    }

    private Map<Long, List<WorkerTask>> tasksByJobId(List<VodJob> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }

        List<Long> jobIds = jobs.stream()
                .map(VodJob::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<WorkerTask>> tasksByJobId = new LinkedHashMap<>();
        workerTaskRepository.findAllByVodJobIdInOrderByVodJobIdAscIdAsc(jobIds)
                .forEach(task -> tasksByJobId.computeIfAbsent(
                        task.getVodJob().getId(),
                        ignored -> new java.util.ArrayList<>()
                ).add(task));
        return tasksByJobId;
    }

    private VodJob requireJob(Long jobId) {
        return vodJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
    }

    private VodJob createJob(String sourceType, String sourceUrl, String originalFilename) {
        Instant now = Instant.now();

        VodJob job = new VodJob();
        job.setSourceType(sourceType);
        job.setSourceUrl(sourceUrl);
        job.setOriginalFilename(originalFilename);
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setProcessingVersion(1L);

        return vodJobRepository.save(job);
    }

    private VodJob recomputeAndPersistJob(VodJob job) {
        JobProjection.recomputeFromTasks(
                job,
                workerTaskRepository.findAllByVodJobIdOrderByCreatedAtAscIdAsc(job.getId())
        );
        return vodJobRepository.save(job);
    }

    private VodJob queueCreatedJob(VodJob job) {
        Instant queuedAt = Instant.now();
        workerTaskOrchestrationService.queueDownloadJob(job, queuedAt);

        VodJob queuedJob = vodJobRepository.save(job);
        workerTaskRepository.save(workerTaskOrchestrationService.prepareWorkerTask(
                queuedJob,
                queuedJob.getProcessingVersion(),
                WorkerTaskType.DOWNLOAD,
                null,
                queuedAt
        ));
        jobEventRepository.save(JobEvent.create(
                queuedJob,
                EVENT_JOB_CREATED,
                "Job created",
                queuedJob.getCreatedAt()
        ));
        jobEventRepository.save(JobEvent.create(
                queuedJob,
                EVENT_JOB_QUEUED_FOR_DOWNLOAD,
                "Job queued for download worker",
                queuedAt
        ));
        incrementCounterAfterCommit(METRIC_JOBS_CREATED, "source_type", queuedJob.getSourceType());
        return queuedJob;
    }

    private JobDetailResponse toJobDetailResponse(VodJob job) {
        return JobMapper.toDetailResponse(
                job,
                workerExecutionRepository.findFirstByVodJobIdOrderByIdDesc(job.getId()),
                workerTaskRepository.findFirstByVodJobIdOrderByIdDesc(job.getId())
        );
    }

    private ClipCandidateResponse moderateCandidate(Long candidateId, ModerationStatus moderationStatus) {
        ClipCandidate candidate = clipCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found: " + candidateId));
        candidate.setModerationStatus(moderationStatus);
        ClipCandidate savedCandidate = clipCandidateRepository.save(candidate);
        tryAutoCompleteJob(candidate.getVodJob());
        return toCandidateResponse(savedCandidate);
    }

    private void tryAutoCompleteJob(VodJob job) {
        if (job.getStatus() != JobStatus.READY_FOR_REVIEW) {
            return;
        }
        if (clipCandidateRepository.existsByVodJobIdAndModerationStatus(job.getId(), ModerationStatus.PENDING)) {
            return;
        }
        if (clipCandidateRepository.existsByVodJobIdAndApprovedButNotExported(job.getId())) {
            return;
        }

        Instant now = Instant.now();
        JobProjection.applyCompletedExport(job, now);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_COMPLETED,
                "Job auto-completed: all candidates resolved",
                now
        ));
        incrementCounterAfterCommit(METRIC_JOBS_COMPLETED);
        log.info("job_completed jobId={} trigger=auto", job.getId());
    }

    private ClipCandidate requireCandidate(Long candidateId) {
        return clipCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found: " + candidateId));
    }

    private String resolvePlannedExportArtifactPath(ClipCandidate candidate) {
        String currentReference = resolveCurrentExportArtifactReference(candidate);
        if (currentReference != null && !currentReference.isBlank()) {
            return currentReference;
        }
        return normalizeArtifactPath(storageService.resolveExportedClipPath(
                candidate.getVodJob().getId(),
                candidate.getId(),
                ".mp4"
        ).toString());
    }

    private String resolveCurrentExportArtifactReference(ClipCandidate candidate) {
        if (candidate.getExportedClipPath() == null || candidate.getExportedClipPath().isBlank()) {
            return null;
        }
        return normalizeArtifactPath(candidate.getExportedClipPath());
    }

    private String resolveSourceVideoReference(VodJob job) {
        if (job.getSourceVideoReference() != null && !job.getSourceVideoReference().isBlank()) {
            return normalizeArtifactPath(job.getSourceVideoReference());
        }
        if (job.getStorageVideoPath() != null && !job.getStorageVideoPath().isBlank()) {
            return normalizeArtifactPath(job.getStorageVideoPath());
        }
        return null;
    }

    private static String normalizeArtifactPath(String path) {
        return path.replace('\\', '/');
    }

    private void persistSourceVideoReference(VodJob job, Path localSourceVideoPath, String originalFilename) {
        try {
            String storedReference = artifactStorageService.storeSourceVideo(job.getId(), originalFilename, localSourceVideoPath);
            job.setSourceVideoReference(normalizeArtifactPath(storedReference));
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to persist source video artifact",
                    ex
            );
        }
    }

    private void deleteLocalFileIfPresent(Long jobId, String storedPath, String description) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        try {
            Path resolved = PathSafety.requireWithinRoot(
                    storageProperties.getLocalRoot(),
                    Path.of(storedPath),
                    description
            );
            Files.deleteIfExists(resolved);
        } catch (InvalidPathException | IOException ex) {
            log.warn("job_delete_{}_failed jobId={} path={} message={}", description.replace(' ', '_'), jobId, storedPath, ex.getMessage());
        }
    }

    private void deleteArtifactReferenceIfDistinct(Long jobId, String reference, String localPath, String description) {
        if (reference == null || reference.isBlank()) {
            return;
        }
        String normalizedReference = normalizeArtifactPath(reference);
        String normalizedLocalPath = localPath == null || localPath.isBlank()
                ? null
                : normalizeArtifactPath(localPath);
        if (normalizedReference.equals(normalizedLocalPath)) {
            return;
        }
        try {
            artifactStorageService.delete(reference);
        } catch (IOException ex) {
            log.warn(
                    "job_delete_{}_reference_failed jobId={} reference={} message={}",
                    description.replace(' ', '_'),
                    jobId,
                    reference,
                    ex.getMessage()
            );
        }
    }

    private static String validateHttpUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        URI parsed;
        try {
            parsed = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must be a valid http or https URL", ex);
        }
        String scheme = parsed.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must use http or https");
        }
        if (parsed.getHost() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must include a host");
        }
        return parsed.toString();
    }

    private String normalizeWorkerPath(String rawPath, String description) {
        try {
            return normalizeArtifactPath(PathSafety.requireWithinRoot(
                    storageProperties.getLocalRoot(),
                    Path.of(rawPath),
                    description
            ).toString());
        } catch (InvalidPathException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, description + " is invalid", ex);
        }
    }

    private ExportStatusResponse toExportStatusResponse(
            ClipCandidate candidate,
            String artifactPath,
            boolean exportReady
    ) {
        return new ExportStatusResponse(
                candidate.getId(),
                candidate.getVodJob().getId(),
                candidate.getExportStatus().name(),
                artifactPath,
                candidate.getModerationStatus().name(),
                exportReady,
                resolvePreferredExportDownloadUrl(candidate.getId(), artifactPath, exportReady)
        );
    }

    private ClipCandidateResponse toCandidateResponse(ClipCandidate candidate) {
        String artifactReference = resolveCurrentExportArtifactReference(candidate);
        boolean exportReady = exportReady(candidate);
        return ClipCandidateMapper.toResponse(
                candidate,
                exportReady,
                resolvePreferredExportDownloadUrl(candidate.getId(), artifactReference, exportReady)
        );
    }

    private String resolvePreferredExportDownloadUrl(Long candidateId, String artifactReference, boolean exportReady) {
        if (!exportReady || artifactReference == null || artifactReference.isBlank()) {
            return null;
        }
        return artifactStorageService.createSignedGetUri(artifactReference)
                .map(java.net.URI::toString)
                .orElseGet(() -> "/api/exports/" + candidateId + "/file");
    }

    private boolean exportReady(ClipCandidate candidate) {
        return candidate.getExportStatus() == ExportStatus.COMPLETED
                && artifactStorageService.exists(candidate.getExportedClipPath());
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void clearWorkerProgressEvents(Long jobId) {
        jobEventRepository.deleteAllByVodJobIdAndEventType(jobId, EVENT_WORKER_PROGRESS);
    }

    private static String readyForReviewProgressMessage(int clipCandidateCount) {
        if (clipCandidateCount == 0) {
            return "Analysis completed but no non-overlapping clip candidates were found";
        }
        if (clipCandidateCount == 1) {
            return "Analysis completed and 1 clip candidate is ready for review";
        }
        return "Analysis completed and " + clipCandidateCount + " clip candidates are ready for review";
    }

    private static String readyForReviewEventMessage(int clipCandidateCount) {
        if (clipCandidateCount == 0) {
            return "Worker processing completed but no non-overlapping clip candidates were found";
        }
        if (clipCandidateCount == 1) {
            return "Worker processing completed and 1 clip candidate is ready for review";
        }
        return "Worker processing completed and " + clipCandidateCount + " clip candidates are ready for review";
    }

    private Page<ClipCandidate> findCandidatePage(Long jobId, int pageNumber, int pageSize) {
        PageRequest requestedPage = PageRequest.of(Math.max(pageNumber - 1, 0), pageSize, CANDIDATE_SORT);
        Page<ClipCandidate> candidatePage = clipCandidateRepository.findAllByVodJobId(jobId, requestedPage);

        if (candidatePage.getTotalPages() > 0 && pageNumber > candidatePage.getTotalPages()) {
            PageRequest lastPage = PageRequest.of(candidatePage.getTotalPages() - 1, pageSize, CANDIDATE_SORT);
            return clipCandidateRepository.findAllByVodJobId(jobId, lastPage);
        }

        return candidatePage;
    }

    private void updateExecutionTaskAsRunning(WorkerExecution execution, Instant now) {
        WorkerTask task = execution.getWorkerTask();
        if (task == null) {
            return;
        }
        task.markRunning(now);
        workerTaskRepository.save(task);
    }

    private void completeExecutionTask(WorkerExecution execution, Instant now) {
        WorkerTask task = execution.getWorkerTask();
        if (task == null) {
            return;
        }
        task.markSucceeded(now);
        workerTaskRepository.save(task);
    }

    private void failExecutionTask(WorkerExecution execution, Instant now, String failureSummary) {
        WorkerTask task = execution.getWorkerTask();
        if (task == null) {
            return;
        }
        task.markFailed(now, failureSummary);
        workerTaskRepository.save(task);
    }

    private void incrementCounterAfterCommit(String metricName, String... tags) {
        incrementCounterAfterCommit(metricName, 1.0d, tags);
    }

    private void incrementCounterAfterCommit(String metricName, double amount, String... tags) {
        Runnable incrementer = () -> meterRegistry.counter(metricName, tags).increment(amount);
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            incrementer.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                incrementer.run();
            }
        });
    }

    private static String normalizeWorkerRole(String workerRole) {
        String normalized = workerRole == null ? "" : workerRole.trim().toLowerCase();
        if (!WORKER_ROLE_DOWNLOAD.equals(normalized)
                && !WORKER_ROLE_PROCESSING.equals(normalized)
                && !WORKER_ROLE_EXPORT.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workerRole must be 'download', 'processing', or 'export'");
        }
        return normalized;
    }
}
