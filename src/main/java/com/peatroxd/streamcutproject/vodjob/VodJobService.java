package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidatePersistenceMapper;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ExportStatus;
import com.peatroxd.streamcutproject.clipcandidate.ModerationStatus;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateMapper;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowRepository;
import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowPersistenceMapper;
import com.peatroxd.streamcutproject.silence.SilenceSegmentPersistenceMapper;
import com.peatroxd.streamcutproject.silence.SilenceSegmentRepository;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobMapper;
import com.peatroxd.streamcutproject.vodjob.api.TranscriptSegmentResponse;
import com.peatroxd.streamcutproject.vodjob.event.JobEvent;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentRepository;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentPersistenceMapper;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.storage.StorageService;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import com.peatroxd.streamcutproject.storage.PathSafety;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayloadFactory;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDownloadResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerExportResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerFailureReportPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProcessingResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProgressUpdatePayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPort;
import com.peatroxd.streamcutproject.workerdispatch.WorkerTransportAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class VodJobService {

    private static final Logger log = LoggerFactory.getLogger(VodJobService.class);

    private static final String SOURCE_TYPE_URL = "URL";
    private static final String SOURCE_TYPE_FILE = "FILE";
    private static final String WORKER_ROLE_DOWNLOAD = "download";
    private static final String WORKER_ROLE_PROCESSING = "processing";
    private static final String EVENT_JOB_CREATED = "JOB_CREATED";
    private static final String EVENT_JOB_QUEUED_FOR_DOWNLOAD = "JOB_QUEUED_FOR_DOWNLOAD";
    private static final String EVENT_JOB_QUEUED_FOR_PROCESSING = "JOB_QUEUED_FOR_PROCESSING";
    private static final String EVENT_JOB_CLAIMED = "JOB_CLAIMED";
    private static final String EVENT_JOB_CANCELED = "JOB_CANCELED";
    private static final String EVENT_JOB_RESTARTED = "JOB_RESTARTED";
    private static final String EVENT_WORKER_PROGRESS = "WORKER_PROGRESS";
    private static final String EVENT_DOWNLOAD_COMPLETED = "JOB_DOWNLOAD_COMPLETED";
    private static final String EVENT_JOB_READY_FOR_REVIEW = "JOB_READY_FOR_REVIEW";
    private static final String EVENT_JOB_FAILED = "JOB_FAILED";
    private static final String EVENT_EXPORT_STARTED = "EXPORT_STARTED";
    private static final String EVENT_EXPORT_COMPLETED = "EXPORT_COMPLETED";
    private static final Set<JobStatus> ANALYSIS_ACTIVE_STATUSES = EnumSet.of(
            JobStatus.QUEUED_FOR_DOWNLOAD,
            JobStatus.DOWNLOADING,
            JobStatus.QUEUED_FOR_PROCESSING,
            JobStatus.EXTRACTING_AUDIO,
            JobStatus.TRANSCRIBING,
            JobStatus.DETECTING_SILENCE,
            JobStatus.ANALYZING_WINDOWS,
            JobStatus.GENERATING_CANDIDATES
    );
    private static final Set<JobStatus> RESTARTABLE_STATUSES = EnumSet.of(
            JobStatus.QUEUED_FOR_DOWNLOAD,
            JobStatus.DOWNLOADING,
            JobStatus.QUEUED_FOR_PROCESSING,
            JobStatus.EXTRACTING_AUDIO,
            JobStatus.TRANSCRIBING,
            JobStatus.DETECTING_SILENCE,
            JobStatus.ANALYZING_WINDOWS,
            JobStatus.GENERATING_CANDIDATES,
            JobStatus.EXPORTING_CLIP,
            JobStatus.FAILED,
            JobStatus.CANCELED
    );
    private static final Set<JobStatus> CANCELABLE_STATUSES = EnumSet.of(
            JobStatus.QUEUED_FOR_DOWNLOAD,
            JobStatus.DOWNLOADING,
            JobStatus.QUEUED_FOR_PROCESSING,
            JobStatus.EXTRACTING_AUDIO,
            JobStatus.TRANSCRIBING,
            JobStatus.DETECTING_SILENCE,
            JobStatus.ANALYZING_WINDOWS,
            JobStatus.GENERATING_CANDIDATES,
            JobStatus.EXPORTING_CLIP
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
    private final WorkerDispatchPort workerDispatchPort;
    private final WorkerDispatchPayloadFactory workerDispatchPayloadFactory;

    public VodJobService(
            VodJobRepository vodJobRepository,
            JobEventRepository jobEventRepository,
            TranscriptSegmentRepository transcriptSegmentRepository,
            SilenceSegmentRepository silenceSegmentRepository,
            AnalysisWindowRepository analysisWindowRepository,
            ClipCandidateRepository clipCandidateRepository,
            StorageService storageService,
            ArtifactStorageService artifactStorageService,
            StorageProperties storageProperties,
            WorkerDispatchPort workerDispatchPort,
            WorkerDispatchPayloadFactory workerDispatchPayloadFactory) {
        this.vodJobRepository = vodJobRepository;
        this.jobEventRepository = jobEventRepository;
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.silenceSegmentRepository = silenceSegmentRepository;
        this.analysisWindowRepository = analysisWindowRepository;
        this.clipCandidateRepository = clipCandidateRepository;
        this.storageService = storageService;
        this.artifactStorageService = artifactStorageService;
        this.storageProperties = storageProperties;
        this.workerDispatchPort = workerDispatchPort;
        this.workerDispatchPayloadFactory = workerDispatchPayloadFactory;
    }

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
        return vodJobRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "id"))
                .stream()
                .map(JobMapper::toListItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getJob(Long jobId) {
        VodJob job = requireJob(jobId);
        return JobMapper.toDetailResponse(job);
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
                        segment.getWordCount()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClipCandidateResponse> listCandidates(Long jobId) {
        requireJob(jobId);
        return clipCandidateRepository.findAllByJobIdOrderByScoreDescStartSecAscIdAsc(jobId)
                .stream()
                .map(candidate -> ClipCandidateMapper.toResponse(
                        candidate,
                        exportReady(candidate)
                ))
                .toList();
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
        if (job.getStatus() == JobStatus.EXPORTING_CLIP) {
            clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.IN_PROGRESS)
                    .forEach(candidate -> {
                        candidate.setExportStatus(ExportStatus.FAILED);
                        clipCandidateRepository.save(candidate);
                    });
        }

        job.setProcessingVersion(nextProcessingVersion(job));
        job.setStatus(JobStatus.CANCELED);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(0);
        job.setProgressMessage("Worker run canceled by operator");
        job.setErrorMessage("Canceled by operator");
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CANCELED,
                "Operator canceled the current worker run",
                now
        ));
        log.warn("job_canceled jobId={} processingVersion={}", job.getId(), job.getProcessingVersion());

        return JobMapper.toDetailResponse(job);
    }

    @Transactional
    public JobDetailResponse restartJob(Long jobId) {
        VodJob job = requireJob(jobId);
        if (!RESTARTABLE_STATUSES.contains(job.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job cannot be restarted from status: " + job.getStatus()
            );
        }

        Instant now = Instant.now();
        if (shouldRestartExport(job)) {
            restartExport(job, now);
        } else {
            resetAnalysisArtifacts(job);
            queueJobForDownload(job, now);
        }

        job.setProcessingVersion(nextProcessingVersion(job));
        job.setUpdatedAt(now);
        job.setFinishedAt(null);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(null);
        job.setErrorMessage(null);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_RESTARTED,
                "Operator restarted the worker run",
                now
        ));
        if (job.getStatus() == JobStatus.QUEUED_FOR_DOWNLOAD) {
            jobEventRepository.save(JobEvent.create(
                    job,
                    EVENT_JOB_QUEUED_FOR_DOWNLOAD,
                    "Job queued for download worker after restart",
                    now
            ));
        }
        log.info("job_restarted jobId={} processingVersion={} status={}", job.getId(), job.getProcessingVersion(), job.getStatus());

        return JobMapper.toDetailResponse(job);
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
        String artifactPath = resolveExportArtifactPath(candidate);

        candidate.setExportedClipPath(artifactPath);
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setUpdatedAt(now);
        job.setFinishedAt(null);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(null);
        job.setProgressPercent(90);
        job.setProgressMessage("Queued for clip export");
        job.setErrorMessage(null);

        clipCandidateRepository.save(candidate);
        vodJobRepository.save(job);
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
        return toExportStatusResponse(candidate, resolveExportArtifactPath(candidate), exportReady(candidate));
    }

    @Transactional(readOnly = true)
    public String getExportArtifactReference(Long exportId) {
        ClipCandidate candidate = requireCandidate(exportId);
        String reference = resolveExportArtifactPath(candidate);
        if (candidate.getExportStatus() != ExportStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Export artifact is not ready for candidate: " + exportId
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

        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromDownloadJob(job);
        workerDispatchPort.dispatch(payload);

        Instant now = Instant.now();
        queueJobForDownload(job, now);
        vodJobRepository.save(job);

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
    public Optional<WorkerDispatchPayload> claimNextQueuedJob(String workerId, String workerRole) {
        String normalizedRole = normalizeWorkerRole(workerRole);
        if (WORKER_ROLE_PROCESSING.equals(normalizedRole)) {
            return claimNextProcessingJob(workerId);
        }
        return claimNextDownloadJob(workerId);
    }

    private Optional<WorkerDispatchPayload> claimNextDownloadJob(String workerId) {
        List<VodJob> queuedJobs = vodJobRepository.findAllByStatusForUpdate(
                JobStatus.QUEUED_FOR_DOWNLOAD,
                PageRequest.of(0, 1)
        );
        if (queuedJobs.isEmpty()) {
            return Optional.empty();
        }

        VodJob job = queuedJobs.get(0);
        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromDownloadJob(job);

        Instant now = Instant.now();
        job.setStatus(JobStatus.DOWNLOADING);
        job.setUpdatedAt(now);
        job.setCurrentWorkerId(workerId);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(JobStatus.DOWNLOADING));
        job.setProgressMessage("Download worker claimed job and started source materialization");
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CLAIMED,
                "Download claimed by worker " + workerId,
                now
        ));
        log.info("download_claimed jobId={} workerId={} status={}", job.getId(), workerId, job.getStatus());

        return Optional.of(payload);
    }

    private Optional<WorkerDispatchPayload> claimNextProcessingJob(String workerId) {
        List<ClipCandidate> pendingExports = clipCandidateRepository.findPendingExportsForUpdate(
                ExportStatus.IN_PROGRESS,
                PageRequest.of(0, 1)
        );
        if (!pendingExports.isEmpty()) {
            ClipCandidate candidate = pendingExports.get(0);
            Instant now = Instant.now();
            candidate.getVodJob().setCurrentWorkerId(workerId);
            candidate.getVodJob().setLastWorkerHeartbeatAt(now);
            candidate.getVodJob().setProgressPercent(92);
            candidate.getVodJob().setProgressMessage("Worker claimed export task");
            candidate.getVodJob().setUpdatedAt(now);
            vodJobRepository.save(candidate.getVodJob());
            jobEventRepository.save(JobEvent.create(
                    candidate.getVodJob(),
                    EVENT_JOB_CLAIMED,
                    "Export claimed by worker " + workerId + " for candidate " + candidate.getId(),
                    now
            ));
            log.info(
                    "export_claimed jobId={} candidateId={} workerId={} status={}",
                    candidate.getVodJob().getId(),
                    candidate.getId(),
                    workerId,
                    candidate.getVodJob().getStatus()
            );
            return Optional.of(workerDispatchPayloadFactory.fromExportCandidate(candidate));
        }

        List<VodJob> queuedJobs = vodJobRepository.findAllByStatusForUpdate(
                JobStatus.QUEUED_FOR_PROCESSING,
                PageRequest.of(0, 1)
        );
        if (queuedJobs.isEmpty()) {
            return Optional.empty();
        }

        VodJob job = queuedJobs.get(0);
        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromAnalyzeJob(job);

        Instant now = Instant.now();
        job.setStatus(JobStatus.EXTRACTING_AUDIO);
        job.setUpdatedAt(now);
        job.setCurrentWorkerId(workerId);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(JobStatus.EXTRACTING_AUDIO));
        job.setProgressMessage("Processing worker claimed job and started analysis pipeline");
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CLAIMED,
                "Analysis claimed by worker " + workerId,
                now
        ));
        log.info("analysis_claimed jobId={} workerId={} status={}", job.getId(), workerId, job.getStatus());

        return Optional.of(payload);
    }

    @Transactional
    public WorkerTransportAck updateWorkerProgress(WorkerProgressUpdatePayload payload) {
        VodJob job = requireJob(payload.jobId());
        validateWorkerLease(job, payload.workerId(), payload.processingVersion());

        JobStatus stageStatus = parseJobStatus(payload.status(), "worker progress status");
        Instant now = Instant.now();
        job.setStatus(stageStatus);
        job.setUpdatedAt(now);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(payload.progressPercent());
        job.setProgressMessage(payload.message());
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
        validateWorkerLease(job, payload.workerId(), payload.processingVersion());

        transcriptSegmentRepository.deleteAllByJobId(job.getId());
        silenceSegmentRepository.deleteAllByJobId(job.getId());
        analysisWindowRepository.deleteAllByJobId(job.getId());
        clipCandidateRepository.deleteAllByJobId(job.getId());

        transcriptSegmentRepository.saveAll(TranscriptSegmentPersistenceMapper.toEntities(
                job,
                safeList(payload.transcriptSegments())
        ));
        silenceSegmentRepository.saveAll(SilenceSegmentPersistenceMapper.toEntities(
                job,
                safeList(payload.silenceSegments())
        ));
        analysisWindowRepository.saveAll(AnalysisWindowPersistenceMapper.toEntities(
                job,
                safeList(payload.analysisWindows())
        ));
        clipCandidateRepository.saveAll(ClipCandidatePersistenceMapper.toEntities(
                job,
                safeList(payload.clipCandidates())
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
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(100);
        job.setProgressMessage("Analysis completed and candidates are ready for review");
        job.setErrorMessage(null);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_READY_FOR_REVIEW,
                "Worker processing completed and job is ready for review",
                now
        ));
        log.info(
                "job_result_ingested jobId={} status={} transcriptSegments={} silenceSegments={} analysisWindows={} clipCandidates={}",
                job.getId(),
                job.getStatus(),
                safeList(payload.transcriptSegments()).size(),
                safeList(payload.silenceSegments()).size(),
                safeList(payload.analysisWindows()).size(),
                safeList(payload.clipCandidates()).size()
        );

        return new WorkerTransportAck(job.getId(), JobStatus.READY_FOR_REVIEW.name());
    }

    @Transactional
    public WorkerTransportAck ingestWorkerDownloadResult(WorkerDownloadResultPayload payload) {
        VodJob job = requireJob(payload.jobId());
        validateWorkerLease(job, payload.workerId(), payload.processingVersion());

        Instant now = Instant.now();
        job.setStorageVideoPath(normalizeWorkerPath(payload.videoPath(), "worker download video path"));
        job.setStatus(JobStatus.QUEUED_FOR_PROCESSING);
        job.setUpdatedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(progressPercentFor(JobStatus.QUEUED_FOR_PROCESSING));
        job.setProgressMessage("Source video is ready and queued for processing");
        job.setErrorMessage(null);
        vodJobRepository.save(job);
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
        validateWorkerLease(job, payload.workerId(), payload.processingVersion());
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
        job.setStatus(JobStatus.COMPLETED);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(100);
        job.setProgressMessage("Export completed and clip artifact is ready");
        job.setErrorMessage(null);
        clipCandidateRepository.save(candidate);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_EXPORT_COMPLETED,
                "Export completed for candidate " + candidate.getId(),
                now
        ));
        log.info(
                "export_completed jobId={} candidateId={} status={} artifactPath={}",
                job.getId(),
                candidate.getId(),
                job.getStatus(),
                candidate.getExportedClipPath()
        );

        return new WorkerTransportAck(job.getId(), JobStatus.COMPLETED.name());
    }

    @Transactional
    public WorkerTransportAck reportWorkerFailure(WorkerFailureReportPayload payload) {
        VodJob job = requireJob(payload.jobId());
        validateWorkerLease(job, payload.workerId(), payload.processingVersion());
        Instant now = Instant.now();
        String failureSummary = payload.failedState() + ": " + payload.message();

        if ("EXPORTING_CLIP".equals(payload.failedState())) {
            clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.IN_PROGRESS)
                    .forEach(candidate -> {
                        candidate.setExportStatus(ExportStatus.FAILED);
                        clipCandidateRepository.save(candidate);
                    });
        }

        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(failureSummary);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(now);
        job.setProgressPercent(0);
        job.setProgressMessage("Worker run failed");
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_FAILED,
                "Worker reported failure in state " + payload.failedState() + ": " + payload.message(),
                now
        ));
        log.warn(
                "job_failed jobId={} failedState={} status={} message={}",
                job.getId(),
                payload.failedState(),
                job.getStatus(),
                payload.message()
        );

        return new WorkerTransportAck(job.getId(), JobStatus.FAILED.name());
    }

    private void validateWorkerLease(VodJob job, String workerId, Long processingVersion) {
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
    }

    private static JobStatus parseJobStatus(String rawStatus, String description) {
        try {
            return JobStatus.valueOf(String.valueOf(rawStatus).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, description + " is invalid", ex);
        }
    }

    private static int progressPercentFor(JobStatus status) {
        return switch (status) {
            case NEW -> 0;
            case QUEUED_FOR_DOWNLOAD -> 5;
            case DOWNLOADING -> 18;
            case QUEUED_FOR_PROCESSING -> 28;
            case EXTRACTING_AUDIO -> 36;
            case TRANSCRIBING -> 48;
            case DETECTING_SILENCE -> 68;
            case ANALYZING_WINDOWS -> 84;
            case GENERATING_CANDIDATES -> 94;
            case READY_FOR_REVIEW, COMPLETED -> 100;
            case EXPORTING_CLIP -> 92;
            case CANCELED, FAILED -> 0;
        };
    }

    private boolean shouldRestartExport(VodJob job) {
        if (job.getStatus() == JobStatus.EXPORTING_CLIP) {
            return true;
        }
        return clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.FAILED)
                .stream()
                .findFirst()
                .isPresent();
    }

    private void restartExport(VodJob job, Instant now) {
        List<ClipCandidate> failedExports = clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.FAILED);
        List<ClipCandidate> runningExports = clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.IN_PROGRESS);
        ClipCandidate candidate = !runningExports.isEmpty() ? runningExports.getFirst() : (!failedExports.isEmpty() ? failedExports.getFirst() : null);
        if (candidate == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No export candidate is available to restart for job: " + job.getId()
            );
        }
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);
        clipCandidateRepository.save(candidate);
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setProgressPercent(progressPercentFor(JobStatus.EXPORTING_CLIP));
        job.setProgressMessage("Queued for clip export");
        job.setStartedAt(job.getStartedAt() == null ? now : job.getStartedAt());
    }

    private void resetAnalysisArtifacts(VodJob job) {
        transcriptSegmentRepository.deleteAllByJobId(job.getId());
        silenceSegmentRepository.deleteAllByJobId(job.getId());
        analysisWindowRepository.deleteAllByJobId(job.getId());
        clipCandidateRepository.deleteAllByJobId(job.getId());
        job.setStatus(JobStatus.QUEUED_FOR_DOWNLOAD);
        job.setStartedAt(null);
        job.setStorageAudioPath(null);
    }

    private static long nextProcessingVersion(VodJob job) {
        return job.getProcessingVersion() == null ? 1L : job.getProcessingVersion() + 1L;
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

    private VodJob queueCreatedJob(VodJob job) {
        Instant queuedAt = Instant.now();
        queueJobForDownload(job, queuedAt);

        VodJob queuedJob = vodJobRepository.save(job);
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
        return queuedJob;
    }

    private ClipCandidateResponse moderateCandidate(Long candidateId, ModerationStatus moderationStatus) {
        ClipCandidate candidate = clipCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found: " + candidateId));
        candidate.setModerationStatus(moderationStatus);
        ClipCandidate savedCandidate = clipCandidateRepository.save(candidate);
        return ClipCandidateMapper.toResponse(
                savedCandidate,
                exportReady(savedCandidate)
        );
    }

    private ClipCandidate requireCandidate(Long candidateId) {
        return clipCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found: " + candidateId));
    }

    private String resolveExportArtifactPath(ClipCandidate candidate) {
        if (candidate.getExportedClipPath() != null && !candidate.getExportedClipPath().isBlank()) {
            return normalizeArtifactPath(candidate.getExportedClipPath());
        }
        return normalizeArtifactPath(storageService.resolveExportedClipPath(
                candidate.getVodJob().getId(),
                candidate.getId(),
                ".mp4"
        ).toString());
    }

    private static String normalizeArtifactPath(String path) {
        return path.replace('\\', '/');
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
                exportReady
        );
    }

    private boolean exportReady(ClipCandidate candidate) {
        return candidate.getExportStatus() == ExportStatus.COMPLETED
                && artifactStorageService.exists(candidate.getExportedClipPath());
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void queueJobForDownload(VodJob job, Instant queuedAt) {
        job.setStatus(JobStatus.QUEUED_FOR_DOWNLOAD);
        job.setUpdatedAt(queuedAt);
        job.setCurrentWorkerId(null);
        job.setLastWorkerHeartbeatAt(null);
        job.setProgressPercent(progressPercentFor(JobStatus.QUEUED_FOR_DOWNLOAD));
        job.setProgressMessage("Queued for download worker");
        job.setFinishedAt(null);
        job.setErrorMessage(null);
    }

    private static String normalizeWorkerRole(String workerRole) {
        String normalized = workerRole == null ? "" : workerRole.trim().toLowerCase();
        if (!WORKER_ROLE_DOWNLOAD.equals(normalized) && !WORKER_ROLE_PROCESSING.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workerRole must be 'download' or 'processing'");
        }
        return normalized;
    }
}
