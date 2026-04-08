package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayloadFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerDispatchPayloadFactoryTest {

    private final WorkerDispatchPayloadFactory factory = new WorkerDispatchPayloadFactory();

    @Test
    void buildsAnalyzePayloadFromJobContractFields() {
        VodJob job = new VodJob();
        job.setId(42L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");
        job.setStorageVideoPath("/var/lib/streamcut/jobs/42/source/video.mp4");
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-04-05T10:00:00Z"));

        WorkerDispatchPayload payload = factory.fromAnalyzeJob(job);

        assertThat(payload.jobId()).isEqualTo(42L);
        assertThat(payload.taskType()).isEqualTo("ANALYZE");
        assertThat(payload.videoPath()).isEqualTo("/var/lib/streamcut/jobs/42/source/video.mp4");
        assertThat(payload.sourceType()).isEqualTo("URL");
        assertThat(payload.sourceUrl()).isEqualTo("https://example.com/video");
    }

    @Test
    void rejectsJobsWithoutStorageVideoPath() {
        VodJob job = new VodJob();
        job.setId(42L);
        job.setSourceType("FILE");

        assertThatThrownBy(() -> factory.fromAnalyzeJob(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no storage video path");
    }

    @Test
    void buildsDownloadPayloadWithoutStorageVideoPath() {
        VodJob job = new VodJob();
        job.setId(42L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");

        WorkerDispatchPayload payload = factory.fromDownloadJob(job);

        assertThat(payload.jobId()).isEqualTo(42L);
        assertThat(payload.taskType()).isEqualTo("DOWNLOAD");
        assertThat(payload.videoPath()).isNull();
        assertThat(payload.sourceUrl()).isEqualTo("https://example.com/video");
    }
}
