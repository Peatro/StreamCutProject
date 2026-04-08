package com.peatroxd.streamcutproject.retention;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ExportStatus;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.storage.PathSafety;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import com.peatroxd.streamcutproject.vodjob.VodJobRepository;
import com.peatroxd.streamcutproject.vodjob.event.JobEvent;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class RetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupService.class);

    private static final Set<JobStatus> SOURCE_RETENTION_STATUSES = EnumSet.of(JobStatus.COMPLETED, JobStatus.FAILED);
    private static final String EVENT_SOURCE_CLEANED = "SOURCE_CLEANED";
    private static final String EVENT_ARTIFACT_CLEANED = "ARTIFACT_CLEANED";

    private final VodJobRepository vodJobRepository;
    private final ClipCandidateRepository clipCandidateRepository;
    private final JobEventRepository jobEventRepository;
    private final ArtifactStorageService artifactStorageService;
    private final StorageProperties storageProperties;
    private final RetentionProperties retentionProperties;
    private final EntityManager entityManager;

    @Autowired
    public RetentionCleanupService(
            VodJobRepository vodJobRepository,
            ClipCandidateRepository clipCandidateRepository,
            JobEventRepository jobEventRepository,
            ArtifactStorageService artifactStorageService,
            StorageProperties storageProperties,
            RetentionProperties retentionProperties,
            EntityManager entityManager
    ) {
        this.vodJobRepository = vodJobRepository;
        this.clipCandidateRepository = clipCandidateRepository;
        this.jobEventRepository = jobEventRepository;
        this.artifactStorageService = artifactStorageService;
        this.storageProperties = storageProperties;
        this.retentionProperties = retentionProperties;
        this.entityManager = entityManager;
    }

    public RetentionCleanupService(
            VodJobRepository vodJobRepository,
            ClipCandidateRepository clipCandidateRepository,
            JobEventRepository jobEventRepository,
            ArtifactStorageService artifactStorageService,
            StorageProperties storageProperties,
            RetentionProperties retentionProperties
    ) {
        this(
                vodJobRepository,
                clipCandidateRepository,
                jobEventRepository,
                artifactStorageService,
                storageProperties,
                retentionProperties,
                null
        );
    }

    public RetentionCleanupResult cleanupExpiredFiles() {
        return new RetentionCleanupResult(cleanupSourceFiles(), cleanupArtifactFiles());
    }

    @Transactional
    public int cleanupSourceFiles() {
        Instant cleanupTime = Instant.now();
        Instant retainedBefore = cleanupTime.minus(retentionProperties.resolveSourceRetention());
        int cleanedCount = 0;

        for (VodJob job : vodJobRepository.findAllByStatusInAndExpiredSourceRetention(
                SOURCE_RETENTION_STATUSES,
                retainedBefore
        )) {
            if (!isSourceCleanupEligible(job, retainedBefore)) {
                continue;
            }
            if (cleanupSourceFile(job, cleanupTime)) {
                cleanedCount++;
            }
        }

        return cleanedCount;
    }

    @Transactional
    public int cleanupArtifactFiles() {
        Instant cleanupTime = Instant.now();
        Instant retainedBefore = cleanupTime.minus(retentionProperties.resolveArtifactRetention());
        int cleanedCount = 0;

        for (VodJob job : vodJobRepository.findAllByStatusAndExpiredRetention(JobStatus.COMPLETED, retainedBefore)) {
            if (!isArtifactCleanupEligible(job, retainedBefore)) {
                continue;
            }

            List<ClipCandidate> candidates = clipCandidateRepository.findAllByVodJobIdAndExportStatus(
                    job.getId(),
                    ExportStatus.COMPLETED
            );
            for (ClipCandidate candidate : candidates) {
                if (cleanupArtifactFile(job, candidate, cleanupTime)) {
                    cleanedCount++;
                }
            }
        }

        return cleanedCount;
    }

    private boolean cleanupSourceFile(VodJob job, Instant cleanupTime) {
        VodJob currentJob = reloadJob(job);
        if (currentJob == null) {
            log.warn(
                    "source_cleanup_skipped jobId={} reason=job_not_found_during_revalidation",
                    job.getId()
            );
            return false;
        }
        if (!SOURCE_RETENTION_STATUSES.contains(currentJob.getStatus())) {
            log.warn(
                    "source_cleanup_skipped jobId={} status={} reason=status_changed_before_delete",
                    currentJob.getId(),
                    currentJob.getStatus()
            );
            return false;
        }

        String sourcePath = currentJob.getStorageVideoPath();
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }

        try {
            Path resolvedPath = PathSafety.requireWithinRoot(
                    storageProperties.getLocalRoot(),
                    Path.of(sourcePath),
                    "stored source video path"
            );
            boolean existed = Files.exists(resolvedPath);
            Files.deleteIfExists(resolvedPath);
            currentJob.setStorageVideoPath(null);
            vodJobRepository.save(currentJob);
            jobEventRepository.save(JobEvent.create(
                    currentJob,
                    EVENT_SOURCE_CLEANED,
                    sourceCleanupMessage(existed),
                    cleanupTime
            ));
            log.info(
                    "source_cleaned jobId={} path={} deleted={}",
                    currentJob.getId(),
                    resolvedPath,
                    existed
            );
            return true;
        } catch (InvalidPathException | IOException ex) {
            log.warn(
                    "source_cleanup_failed jobId={} path={} message={}",
                    currentJob.getId(),
                    sourcePath,
                    ex.getMessage()
            );
            return false;
        }
    }

    private boolean cleanupArtifactFile(VodJob job, ClipCandidate candidate, Instant cleanupTime) {
        VodJob currentJob = reloadJob(job);
        if (currentJob == null) {
            log.warn(
                    "artifact_cleanup_skipped jobId={} candidateId={} reason=job_not_found_during_revalidation",
                    job.getId(),
                    candidate.getId()
            );
            return false;
        }
        if (currentJob.getStatus() != JobStatus.COMPLETED) {
            log.warn(
                    "artifact_cleanup_skipped jobId={} candidateId={} status={} reason=status_changed_before_delete",
                    currentJob.getId(),
                    candidate.getId(),
                    currentJob.getStatus()
            );
            return false;
        }

        String reference = candidate.getExportedClipPath();
        if (reference == null || reference.isBlank() || candidate.getExportStatus() != ExportStatus.COMPLETED) {
            return false;
        }

        try {
            boolean existed = artifactStorageService.exists(reference);
            if (existed) {
                artifactStorageService.delete(reference);
            }
            candidate.setExportedClipPath(null);
            clipCandidateRepository.save(candidate);
            jobEventRepository.save(JobEvent.create(
                    currentJob,
                    EVENT_ARTIFACT_CLEANED,
                    artifactCleanupMessage(candidate.getId(), existed),
                    cleanupTime
            ));
            log.info(
                    "artifact_cleaned jobId={} candidateId={} reference={} deleted={}",
                    currentJob.getId(),
                    candidate.getId(),
                    reference,
                    existed
            );
            return true;
        } catch (IOException ex) {
            log.warn(
                    "artifact_cleanup_failed jobId={} candidateId={} reference={} message={}",
                    currentJob.getId(),
                    candidate.getId(),
                    reference,
                    ex.getMessage()
            );
            return false;
        }
    }

    private VodJob reloadJob(VodJob job) {
        if (entityManager == null) {
            return job;
        }
        if (entityManager.contains(job)) {
            entityManager.detach(job);
        }
        return vodJobRepository.findById(job.getId()).orElse(null);
    }

    private boolean isSourceCleanupEligible(VodJob job, Instant retainedBefore) {
        return SOURCE_RETENTION_STATUSES.contains(job.getStatus())
                && reachedTerminalStateBefore(job, retainedBefore)
                && job.getStorageVideoPath() != null
                && !job.getStorageVideoPath().isBlank();
    }

    private boolean isArtifactCleanupEligible(VodJob job, Instant retainedBefore) {
        return job.getStatus() == JobStatus.COMPLETED
                && reachedTerminalStateBefore(job, retainedBefore);
    }

    private boolean reachedTerminalStateBefore(VodJob job, Instant retainedBefore) {
        Instant retentionAnchor = job.getFinishedAt() == null ? job.getUpdatedAt() : job.getFinishedAt();
        return retentionAnchor != null && retentionAnchor.isBefore(retainedBefore);
    }

    private static String sourceCleanupMessage(boolean existed) {
        if (existed) {
            return "Retention cleanup removed the source video after the configured retention window";
        }
        return "Retention cleanup found the source video already missing and cleared the stored reference";
    }

    private static String artifactCleanupMessage(Long candidateId, boolean existed) {
        if (existed) {
            return "Retention cleanup removed the export artifact for candidate " + candidateId;
        }
        return "Retention cleanup found the export artifact already missing for candidate " + candidateId
                + " and cleared the stored reference";
    }
}
