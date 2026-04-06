package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidatePersistenceMapper;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
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
import com.peatroxd.streamcutproject.storage.StorageService;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayloadFactory;
import com.peatroxd.streamcutproject.workerdispatch.WorkerExportResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerFailureReportPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProcessingResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPort;
import com.peatroxd.streamcutproject.workerdispatch.WorkerTransportAck;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class VodJobService {

    private static final String SOURCE_TYPE_URL = "URL";
    private static final String SOURCE_TYPE_FILE = "FILE";
    private static final String EVENT_JOB_CREATED = "JOB_CREATED";
    private static final String EVENT_JOB_QUEUED = "JOB_QUEUED";
    private static final String EVENT_JOB_CLAIMED = "JOB_CLAIMED";
    private static final String EVENT_JOB_READY_FOR_REVIEW = "JOB_READY_FOR_REVIEW";
    private static final String EVENT_JOB_FAILED = "JOB_FAILED";
    private static final String EVENT_EXPORT_STARTED = "EXPORT_STARTED";
    private static final String EVENT_EXPORT_COMPLETED = "EXPORT_COMPLETED";

    private final VodJobRepository vodJobRepository;
    private final JobEventRepository jobEventRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final SilenceSegmentRepository silenceSegmentRepository;
    private final AnalysisWindowRepository analysisWindowRepository;
    private final ClipCandidateRepository clipCandidateRepository;
    private final StorageService storageService;
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
            WorkerDispatchPort workerDispatchPort,
            WorkerDispatchPayloadFactory workerDispatchPayloadFactory) {
        this.vodJobRepository = vodJobRepository;
        this.jobEventRepository = jobEventRepository;
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.silenceSegmentRepository = silenceSegmentRepository;
        this.analysisWindowRepository = analysisWindowRepository;
        this.clipCandidateRepository = clipCandidateRepository;
        this.storageService = storageService;
        this.workerDispatchPort = workerDispatchPort;
        this.workerDispatchPayloadFactory = workerDispatchPayloadFactory;
    }

    @Transactional
    public JobSummaryResponse createUrlJob(String url) {
        VodJob savedJob = createJob(SOURCE_TYPE_URL, url, null);
        VodJob queuedJob = queueCreatedJob(savedJob);
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
                .map(ClipCandidateMapper::toResponse)
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
        if (candidate.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate must be approved before export: " + candidateId
            );
        }

        VodJob job = candidate.getVodJob();
        Instant now = Instant.now();
        String artifactPath = resolveExportArtifactPath(candidate);

        candidate.setExportedClipPath(artifactPath);
        job.setStatus(JobStatus.EXPORTING_CLIP);
        job.setUpdatedAt(now);

        clipCandidateRepository.save(candidate);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_EXPORT_STARTED,
                "Export started for candidate " + candidateId,
                now
        ));

        return toExportStatusResponse(candidate, artifactPath, JobStatus.EXPORTING_CLIP);
    }

    @Transactional(readOnly = true)
    public ExportStatusResponse getExportStatus(Long exportId) {
        ClipCandidate candidate = requireCandidate(exportId);
        return toExportStatusResponse(candidate, resolveExportArtifactPath(candidate), candidate.getVodJob().getStatus());
    }

    @Transactional(readOnly = true)
    public Path getExportArtifactPath(Long exportId) {
        ClipCandidate candidate = requireCandidate(exportId);
        Path artifactPath = Path.of(resolveExportArtifactPath(candidate)).normalize();
        if (!Files.exists(artifactPath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Export artifact not found for candidate: " + exportId
            );
        }
        return artifactPath;
    }

    @Transactional
    public WorkerDispatchPayload dispatchJob(Long jobId) {
        VodJob job = vodJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));

        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromJob(job);
        workerDispatchPort.dispatch(payload);

        Instant now = Instant.now();
        job.setStatus(JobStatus.QUEUED);
        job.setUpdatedAt(now);
        vodJobRepository.save(job);

        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_QUEUED,
                "Job queued for worker dispatch",
                now
        ));

        return payload;
    }

    @Transactional
    public Optional<WorkerDispatchPayload> claimNextQueuedJob(String workerId) {
        List<ClipCandidate> pendingExports = clipCandidateRepository.findPendingExportsForUpdate(
                JobStatus.EXPORTING_CLIP,
                ModerationStatus.APPROVED,
                PageRequest.of(0, 1)
        );
        if (!pendingExports.isEmpty()) {
            ClipCandidate candidate = pendingExports.get(0);
            Instant now = Instant.now();
            candidate.getVodJob().setUpdatedAt(now);
            vodJobRepository.save(candidate.getVodJob());
            jobEventRepository.save(JobEvent.create(
                    candidate.getVodJob(),
                    EVENT_JOB_CLAIMED,
                    "Export claimed by worker " + workerId + " for candidate " + candidate.getId(),
                    now
            ));
            return Optional.of(workerDispatchPayloadFactory.fromExportCandidate(candidate));
        }

        List<VodJob> queuedJobs = vodJobRepository.findAllByStatusForUpdate(
                JobStatus.QUEUED,
                PageRequest.of(0, 1)
        );
        if (queuedJobs.isEmpty()) {
            return Optional.empty();
        }

        VodJob job = queuedJobs.get(0);
        WorkerDispatchPayload payload = workerDispatchPayloadFactory.fromJob(job);

        Instant now = Instant.now();
        job.setStatus(JobStatus.DOWNLOADING);
        job.setUpdatedAt(now);
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_CLAIMED,
                "Job claimed by worker " + workerId,
                now
        ));

        return Optional.of(payload);
    }

    @Transactional
    public WorkerTransportAck ingestWorkerResult(WorkerProcessingResultPayload payload) {
        VodJob job = requireJob(payload.jobId());

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
            job.setStorageVideoPath(normalizeArtifactPath(payload.videoPath()));
        }
        if (payload.audioPath() != null && !payload.audioPath().isBlank()) {
            job.setStorageAudioPath(normalizeArtifactPath(payload.audioPath()));
        }
        job.setStatus(JobStatus.READY_FOR_REVIEW);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        job.setErrorMessage(null);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_READY_FOR_REVIEW,
                "Worker processing completed and job is ready for review",
                now
        ));

        return new WorkerTransportAck(job.getId(), JobStatus.READY_FOR_REVIEW.name());
    }

    @Transactional
    public WorkerTransportAck ingestWorkerExportResult(WorkerExportResultPayload payload) {
        ClipCandidate candidate = requireCandidate(payload.candidateId());
        VodJob job = candidate.getVodJob();
        Instant now = Instant.now();

        if (payload.artifactPath() != null && !payload.artifactPath().isBlank()) {
            candidate.setExportedClipPath(normalizeArtifactPath(payload.artifactPath()));
        }
        job.setStatus(JobStatus.COMPLETED);
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        clipCandidateRepository.save(candidate);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_EXPORT_COMPLETED,
                "Export completed for candidate " + candidate.getId(),
                now
        ));

        return new WorkerTransportAck(job.getId(), JobStatus.COMPLETED.name());
    }

    @Transactional
    public WorkerTransportAck reportWorkerFailure(WorkerFailureReportPayload payload) {
        VodJob job = requireJob(payload.jobId());
        Instant now = Instant.now();

        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(payload.message());
        job.setUpdatedAt(now);
        job.setFinishedAt(now);
        vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                job,
                EVENT_JOB_FAILED,
                "Worker reported failure in state " + payload.failedState() + ": " + payload.message(),
                now
        ));

        return new WorkerTransportAck(job.getId(), JobStatus.FAILED.name());
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

        return vodJobRepository.save(job);
    }

    private VodJob queueCreatedJob(VodJob job) {
        Instant queuedAt = Instant.now();
        job.setStatus(JobStatus.QUEUED);
        job.setUpdatedAt(queuedAt);

        VodJob queuedJob = vodJobRepository.save(job);
        jobEventRepository.save(JobEvent.create(
                queuedJob,
                EVENT_JOB_CREATED,
                "Job created",
                queuedJob.getCreatedAt()
        ));
        jobEventRepository.save(JobEvent.create(
                queuedJob,
                EVENT_JOB_QUEUED,
                "Job queued for worker processing",
                queuedAt
        ));
        return queuedJob;
    }

    private ClipCandidateResponse moderateCandidate(Long candidateId, ModerationStatus moderationStatus) {
        ClipCandidate candidate = clipCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found: " + candidateId));
        candidate.setModerationStatus(moderationStatus);
        return ClipCandidateMapper.toResponse(clipCandidateRepository.save(candidate));
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

    private ExportStatusResponse toExportStatusResponse(
            ClipCandidate candidate,
            String artifactPath,
            JobStatus jobStatus
    ) {
        return new ExportStatusResponse(
                candidate.getId(),
                candidate.getVodJob().getId(),
                jobStatus.name(),
                artifactPath,
                candidate.getModerationStatus().name()
        );
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
