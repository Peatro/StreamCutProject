package com.peatroxd.streamcutproject.retention;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ExportStatus;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import com.peatroxd.streamcutproject.vodjob.VodJobRepository;
import com.peatroxd.streamcutproject.vodjob.event.JobEvent;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionCleanupServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private VodJobRepository vodJobRepository;

    @Mock
    private ClipCandidateRepository clipCandidateRepository;

    @Mock
    private JobEventRepository jobEventRepository;

    @Mock
    private ArtifactStorageService artifactStorageService;

    private final StorageProperties storageProperties = new StorageProperties();
    private final RetentionProperties retentionProperties = new RetentionProperties();

    private RetentionCleanupService retentionCleanupService;

    @BeforeEach
    void setUp() {
        storageProperties.setLocalRoot(tempDir);
        retentionProperties.setSourceRetention(Duration.ofDays(7));
        retentionProperties.setArtifactRetention(Duration.ofDays(30));
        retentionCleanupService = new RetentionCleanupService(
                vodJobRepository,
                clipCandidateRepository,
                jobEventRepository,
                artifactStorageService,
                storageProperties,
                retentionProperties
        );
        lenient().when(vodJobRepository.save(any(VodJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(clipCandidateRepository.save(any(ClipCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sourceCleanupSkipsNonTerminalJobs() throws Exception {
        Path sourcePath = tempDir.resolve("jobs/1/source/video.mp4");
        Files.createDirectories(sourcePath.getParent());
        Files.writeString(sourcePath, "video");
        VodJob job = buildJob(1L, JobStatus.NEW, Instant.now().minus(Duration.ofDays(10)));
        job.setStorageVideoPath(sourcePath.toString());

        when(vodJobRepository.findAllByStatusInAndExpiredSourceRetention(anyCollection(), any(Instant.class)))
                .thenReturn(List.of(job));

        int cleanedCount = retentionCleanupService.cleanupSourceFiles();

        assertThat(cleanedCount).isZero();
        assertThat(Files.exists(sourcePath)).isTrue();
        verify(vodJobRepository, never()).save(job);
        verify(jobEventRepository, never()).save(any(JobEvent.class));
    }

    @Test
    void sourceCleanupDeletesAfterRetentionWindow() throws Exception {
        Path sourcePath = tempDir.resolve("jobs/2/source/video.mp4");
        Files.createDirectories(sourcePath.getParent());
        Files.writeString(sourcePath, "video");
        VodJob job = buildJob(2L, JobStatus.COMPLETED, Instant.now().minus(Duration.ofDays(8)));
        job.setStorageVideoPath(sourcePath.toString());

        when(vodJobRepository.findAllByStatusInAndExpiredSourceRetention(anyCollection(), any(Instant.class)))
                .thenReturn(List.of(job));

        int cleanedCount = retentionCleanupService.cleanupSourceFiles();

        assertThat(cleanedCount).isEqualTo(1);
        assertThat(Files.exists(sourcePath)).isFalse();
        assertThat(job.getStorageVideoPath()).isNull();
        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("SOURCE_CLEANED");
    }

    @Test
    void artifactCleanupSkipsJobsWithinRetentionWindow() throws Exception {
        VodJob job = buildJob(3L, JobStatus.COMPLETED, Instant.now().minus(Duration.ofDays(5)));

        when(vodJobRepository.findAllByStatusAndExpiredRetention(eq(JobStatus.COMPLETED), any(Instant.class)))
                .thenReturn(List.of(job));

        int cleanedCount = retentionCleanupService.cleanupArtifactFiles();

        assertThat(cleanedCount).isZero();
        verify(clipCandidateRepository, never()).findAllByVodJobIdAndExportStatus(anyLong(), any(ExportStatus.class));
        verify(artifactStorageService, never()).delete(anyString());
    }

    @Test
    void artifactCleanupDeletesAfterRetentionWindow() throws Exception {
        VodJob job = buildJob(4L, JobStatus.COMPLETED, Instant.now().minus(Duration.ofDays(31)));
        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 12.0, 0.91, "excerpt");
        candidate.setId(7L);
        candidate.setExportStatus(ExportStatus.COMPLETED);
        candidate.setExportedClipPath("s3://streamcut-artifacts/exports/jobs/4/candidate-7.mp4");

        when(vodJobRepository.findAllByStatusAndExpiredRetention(eq(JobStatus.COMPLETED), any(Instant.class)))
                .thenReturn(List.of(job));
        when(clipCandidateRepository.findAllByVodJobIdAndExportStatus(job.getId(), ExportStatus.COMPLETED))
                .thenReturn(List.of(candidate));
        when(artifactStorageService.exists(candidate.getExportedClipPath())).thenReturn(true);

        int cleanedCount = retentionCleanupService.cleanupArtifactFiles();

        assertThat(cleanedCount).isEqualTo(1);
        assertThat(candidate.getExportedClipPath()).isNull();
        verify(artifactStorageService).delete("s3://streamcut-artifacts/exports/jobs/4/candidate-7.mp4");
        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("ARTIFACT_CLEANED");
    }

    private VodJob buildJob(Long id, JobStatus status, Instant terminalAt) {
        VodJob job = instantiateJob();
        job.setId(id);
        job.setStatus(status);
        job.setCreatedAt(terminalAt.minus(Duration.ofDays(1)));
        job.setUpdatedAt(terminalAt);
        job.setFinishedAt(terminalAt);
        job.setProcessingVersion(1L);
        return job;
    }

    private VodJob instantiateJob() {
        try {
            Constructor<VodJob> constructor = VodJob.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to instantiate VodJob for test setup", ex);
        }
    }
}
