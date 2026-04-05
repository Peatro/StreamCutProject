package com.peatroxd.streamcutproject.silence;

import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SilenceSegmentPersistenceMapperTest {

    @Test
    void mapsWorkerPayloadToEntity() {
        VodJob job = buildJob(42L);
        SilenceSegmentWorkerPayload payload = SilenceSegmentPersistenceMapper.payload(
                12.5,
                18.25,
                5.75
        );

        SilenceSegment segment = SilenceSegmentPersistenceMapper.toEntity(job, payload);

        assertThat(segment.getVodJob()).isSameAs(job);
        assertThat(segment.getStartSec()).isEqualTo(12.5);
        assertThat(segment.getEndSec()).isEqualTo(18.25);
        assertThat(segment.getDurationSec()).isEqualTo(5.75);
    }

    @Test
    void mapsMultipleWorkerPayloadsInOrder() {
        VodJob job = buildJob(42L);
        List<SilenceSegment> segments = SilenceSegmentPersistenceMapper.toEntities(
                job,
                List.of(
                        SilenceSegmentPersistenceMapper.payload(20.0, 24.0, 4.0),
                        SilenceSegmentPersistenceMapper.payload(10.0, 15.0, 5.0)
                )
        );

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getStartSec()).isEqualTo(20.0);
        assertThat(segments.get(1).getStartSec()).isEqualTo(10.0);
    }

    private static VodJob buildJob(Long id) {
        VodJob job = newVodJob();
        job.setId(id);
        job.setSourceType("URL");
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-04-05T10:00:00Z"));
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
