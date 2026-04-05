package com.peatroxd.streamcutproject.transcript;

import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import com.peatroxd.streamcutproject.vodjob.VodJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamcut-transcript;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Transactional
class TranscriptSegmentRepositoryTest {

    @Autowired
    private VodJobRepository vodJobRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Test
    void storesMultipleSegmentsForOneJobInStableOrder() {
        VodJob job = vodJobRepository.save(buildJob());
        transcriptSegmentRepository.saveAll(List.of(
                TranscriptSegment.create(job, 15.0, 18.0, "second", 1),
                TranscriptSegment.create(job, 5.0, 9.0, "first", 1)
        ));

        List<TranscriptSegment> segments = transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(job.getId());

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getText()).isEqualTo("first");
        assertThat(segments.get(1).getText()).isEqualTo("second");
    }

    private static VodJob buildJob() {
        VodJob job = newVodJob();
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
