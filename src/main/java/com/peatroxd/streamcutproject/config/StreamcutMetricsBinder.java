package com.peatroxd.streamcutproject.config;

import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StreamcutMetricsBinder implements MeterBinder {

    private static final Set<JobStatus> ACTIVE_PROCESSING_STATUSES = EnumSet.of(
            JobStatus.DOWNLOADING,
            JobStatus.EXTRACTING_AUDIO,
            JobStatus.TRANSCRIBING,
            JobStatus.DETECTING_SILENCE,
            JobStatus.ANALYZING_WINDOWS,
            JobStatus.GENERATING_CANDIDATES
    );

    private final VodJobRepository vodJobRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("streamcut.queue.depth", vodJobRepository, repository -> repository.countByStatus(JobStatus.QUEUED_FOR_DOWNLOAD))
                .description("Jobs currently waiting for a download worker")
                .tag("queue_type", "download")
                .register(registry);
        Gauge.builder("streamcut.queue.depth", vodJobRepository, repository -> repository.countByStatus(JobStatus.QUEUED_FOR_PROCESSING))
                .description("Jobs currently waiting for a processing worker")
                .tag("queue_type", "processing")
                .register(registry);
        Gauge.builder("streamcut.jobs.active", vodJobRepository, repository -> repository.countByStatusIn(ACTIVE_PROCESSING_STATUSES))
                .description("Jobs currently in active processing stages")
                .tag("activity_type", "processing")
                .register(registry);
        Gauge.builder("streamcut.jobs.active", vodJobRepository, repository -> repository.countByStatus(JobStatus.EXPORTING_CLIP))
                .description("Jobs currently exporting a clip")
                .tag("activity_type", "export")
                .register(registry);
    }
}
