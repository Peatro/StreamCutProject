package com.peatroxd.streamcutproject.analysiswindow;

import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisWindowPersistenceMapperTest {

    @Test
    void mapsWorkerPayloadToEntity() {
        VodJob job = buildJob(42L);
        AnalysisWindowWorkerPayload payload = AnalysisWindowPersistenceMapper.payload(
                10.0,
                20.0,
                0.75,
                0.25,
                3,
                0.8,
                0.82
        );

        AnalysisWindow window = AnalysisWindowPersistenceMapper.toEntity(job, payload);

        assertThat(window.getVodJob()).isSameAs(job);
        assertThat(window.getStartSec()).isEqualTo(10.0);
        assertThat(window.getEndSec()).isEqualTo(20.0);
        assertThat(window.getSpeechDensity()).isEqualTo(0.75);
        assertThat(window.getSilenceRatio()).isEqualTo(0.25);
        assertThat(window.getEmotionHits()).isEqualTo(3);
        assertThat(window.getContinuityScore()).isEqualTo(0.8);
        assertThat(window.getTotalScore()).isEqualTo(0.82);
    }

    @Test
    void mapsMultipleWorkerPayloadsInOrder() {
        VodJob job = buildJob(42L);
        List<AnalysisWindow> windows = AnalysisWindowPersistenceMapper.toEntities(
                job,
                List.of(
                        AnalysisWindowPersistenceMapper.payload(20.0, 30.0, 0.5, 0.4, 1, 0.6, 0.55),
                        AnalysisWindowPersistenceMapper.payload(10.0, 20.0, 0.8, 0.2, 2, 0.7, 0.74)
                )
        );

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0).getStartSec()).isEqualTo(20.0);
        assertThat(windows.get(1).getStartSec()).isEqualTo(10.0);
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
