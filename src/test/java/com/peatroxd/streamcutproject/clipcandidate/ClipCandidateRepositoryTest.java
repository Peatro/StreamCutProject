package com.peatroxd.streamcutproject.clipcandidate;

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
        "spring.datasource.url=jdbc:h2:mem:streamcut-candidates;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Transactional
class ClipCandidateRepositoryTest {

    @Autowired
    private VodJobRepository vodJobRepository;

    @Autowired
    private ClipCandidateRepository clipCandidateRepository;

    @Test
    void storesMultipleCandidatesForOneJobInStableOrder() {
        VodJob job = vodJobRepository.save(buildJob());
        clipCandidateRepository.saveAll(List.of(
                ClipCandidate.create(job, 15.0, 22.0, 0.87, "second"),
                ClipCandidate.create(job, 5.0, 12.0, 0.91, "first")
        ));

        List<ClipCandidate> candidates = clipCandidateRepository.findAllByJobIdOrderByScoreDescStartSecAscIdAsc(job.getId());

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).getScore()).isEqualTo(0.91);
        assertThat(candidates.get(0).getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(candidates.get(1).getScore()).isEqualTo(0.87);
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
