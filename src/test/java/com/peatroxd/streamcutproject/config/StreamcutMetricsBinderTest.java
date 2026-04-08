package com.peatroxd.streamcutproject.config;

import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamcutMetricsBinderTest {

    @Mock
    private VodJobRepository vodJobRepository;

    @Test
    void registersQueueDepthAndActiveJobGauges() {
        when(vodJobRepository.countByStatus(JobStatus.QUEUED_FOR_DOWNLOAD)).thenReturn(3L);
        when(vodJobRepository.countByStatus(JobStatus.QUEUED_FOR_PROCESSING)).thenReturn(5L);
        when(vodJobRepository.countByStatusIn(EnumSet.of(
                JobStatus.DOWNLOADING,
                JobStatus.EXTRACTING_AUDIO,
                JobStatus.TRANSCRIBING,
                JobStatus.DETECTING_SILENCE,
                JobStatus.ANALYZING_WINDOWS,
                JobStatus.GENERATING_CANDIDATES
        ))).thenReturn(4L);
        when(vodJobRepository.countByStatus(JobStatus.EXPORTING_CLIP)).thenReturn(2L);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new StreamcutMetricsBinder(vodJobRepository).bindTo(meterRegistry);

        assertThat(meterRegistry.get("streamcut.queue.depth")
                .tag("queue_type", "download")
                .gauge()
                .value()).isEqualTo(3.0d);
        assertThat(meterRegistry.get("streamcut.queue.depth")
                .tag("queue_type", "processing")
                .gauge()
                .value()).isEqualTo(5.0d);
        assertThat(meterRegistry.get("streamcut.jobs.active")
                .tag("activity_type", "processing")
                .gauge()
                .value()).isEqualTo(4.0d);
        assertThat(meterRegistry.get("streamcut.jobs.active")
                .tag("activity_type", "export")
                .gauge()
                .value()).isEqualTo(2.0d);
    }
}
