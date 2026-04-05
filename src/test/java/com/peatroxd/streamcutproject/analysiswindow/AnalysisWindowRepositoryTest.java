package com.peatroxd.streamcutproject.analysiswindow;

import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import com.peatroxd.streamcutproject.vodjob.VodJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamcut-analysis;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Transactional
class AnalysisWindowRepositoryTest {

    @Autowired
    private VodJobRepository vodJobRepository;

    @Autowired
    private AnalysisWindowRepository analysisWindowRepository;

    @Test
    void storesMultipleWindowsForOneJobInStableOrder() {
        VodJob job = vodJobRepository.save(buildJob());
        analysisWindowRepository.saveAll(List.of(
                AnalysisWindow.create(job, 15.0, 20.0, 0.3, 0.7, 1, 0.4, 0.41),
                AnalysisWindow.create(job, 5.0, 10.0, 0.8, 0.2, 2, 0.6, 0.72)
        ));

        List<AnalysisWindow> windows = analysisWindowRepository.findAllByJobIdOrderByStartSecAscIdAsc(job.getId());

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0).getStartSec()).isEqualTo(5.0);
        assertThat(windows.get(1).getStartSec()).isEqualTo(15.0);
        assertThat(windows.get(0).getVodJob().getId()).isEqualTo(job.getId());
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
