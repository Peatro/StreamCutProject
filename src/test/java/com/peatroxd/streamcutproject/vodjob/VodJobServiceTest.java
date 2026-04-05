package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.event.JobEvent;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VodJobServiceTest {

    @Mock
    private VodJobRepository vodJobRepository;

    @Mock
    private JobEventRepository jobEventRepository;

    private VodJobService vodJobService;

    @BeforeEach
    void setUp() {
        vodJobService = new VodJobService(vodJobRepository, jobEventRepository);
    }

    @Test
    void createUrlJobUsesNewStatus() {
        when(vodJobRepository.save(any())).thenAnswer(invocation -> {
            VodJob job = invocation.getArgument(0);
            job.setId(1L);
            return job;
        });

        var response = vodJobService.createUrlJob("https://example.com/video");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.sourceType()).isEqualTo("URL");
        assertThat(response.sourceUrl()).isEqualTo("https://example.com/video");
        verify(vodJobRepository).save(any(VodJob.class));
    }

    @Test
    void createFileJobUsesFileStatusAndOriginalFilename() {
        when(vodJobRepository.save(any())).thenAnswer(invocation -> {
            VodJob job = invocation.getArgument(0);
            job.setId(2L);
            return job;
        });

        var response = vodJobService.createFileJob("video.mp4");

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.sourceType()).isEqualTo("FILE");
        assertThat(response.sourceUrl()).isNull();
        assertThat(response.originalFilename()).isEqualTo("video.mp4");
        verify(vodJobRepository).save(any(VodJob.class));
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
    void listJobEventsReturnsPersistedEventsInStableOrder() {
        VodJob job = buildJob(1L, "https://example.com/video", Instant.parse("2026-04-05T10:00:00Z"));
        JobEvent earlier = Mockito.mock(JobEvent.class);
        when(earlier.getId()).thenReturn(10L);
        when(earlier.getEventType()).thenReturn("JOB_CREATED");
        when(earlier.getMessage()).thenReturn("Job created");
        when(earlier.getCreatedAt()).thenReturn(Instant.parse("2026-04-05T10:00:01Z"));

        JobEvent later = Mockito.mock(JobEvent.class);
        when(later.getId()).thenReturn(11L);
        when(later.getEventType()).thenReturn("JOB_QUEUED");
        when(later.getMessage()).thenReturn("Job queued");
        when(later.getCreatedAt()).thenReturn(Instant.parse("2026-04-05T10:00:02Z"));

        when(vodJobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(earlier, later));

        List<JobEventResponse> events = vodJobService.listJobEvents(1L);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).id()).isEqualTo(10L);
        assertThat(events.get(0).eventType()).isEqualTo("JOB_CREATED");
        assertThat(events.get(1).id()).isEqualTo(11L);
        assertThat(events.get(1).eventType()).isEqualTo("JOB_QUEUED");
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
        return job;
    }
}
