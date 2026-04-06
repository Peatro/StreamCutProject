package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowWorkerPayload;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateRepository;
import com.peatroxd.streamcutproject.clipcandidate.ModerationStatus;
import com.peatroxd.streamcutproject.silence.SilenceSegmentWorkerPayload;
import com.peatroxd.streamcutproject.storage.StorageService;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentWorkerPayload;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import com.peatroxd.streamcutproject.workerdispatch.WorkerExportResultPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerProcessingResultPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamcut-api-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Transactional
class VodJobApiIntegrationTest {

    private static final Path STORAGE_ROOT;

    static {
        try {
            STORAGE_ROOT = Files.createTempDirectory("streamcut-api-flow-storage");
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-root", () -> STORAGE_ROOT.toString());
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private VodJobService vodJobService;

    @Autowired
    private VodJobRepository vodJobRepository;

    @Autowired
    private ClipCandidateRepository clipCandidateRepository;

    @Autowired
    private JobEventRepository jobEventRepository;

    @Autowired
    private StorageService storageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void urlJobCreationAndWorkerClaimArePersistedThroughApis() throws Exception {
        mockMvc.perform(post("/api/jobs/url")
                        .contentType("application/json")
                        .content("""
                                {"url":"https://example.com/video"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.sourceType").value("URL"));

        VodJob job = vodJobRepository.findAll().getFirst();

        mockMvc.perform(post("/api/internal/worker/claims/next")
                        .contentType("application/json")
                        .content("""
                                {"workerId":"worker-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(job.getId()))
                .andExpect(jsonPath("$.taskType").value("ANALYZE"))
                .andExpect(jsonPath("$.sourceType").value("URL"));

        mockMvc.perform(get("/api/jobs/{id}", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWNLOADING"));

        assertThat(jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(job.getId()))
                .extracting(event -> event.getEventType())
                .contains("JOB_CREATED", "JOB_QUEUED", "JOB_CLAIMED");
    }

    @Test
    void candidateApprovalAndExportCompletionArePersistedThroughApis() throws Exception {
        VodJob job = vodJobRepository.save(newUrlJob("https://example.com/video"));

        vodJobService.ingestWorkerResult(new WorkerProcessingResultPayload(
                job.getId(),
                120L,
                "en",
                storageService.resolveSourceVideoPath(job.getId(), "video.mp4").toString(),
                storageService.resolveAudioPath(job.getId()).toString(),
                List.of(new TranscriptSegmentWorkerPayload(0.0, 2.0, "hello", 1)),
                List.of(new SilenceSegmentWorkerPayload(2.0, 3.0, 1.0)),
                List.of(new AnalysisWindowWorkerPayload(0.0, 20.0, 1.0, 0.1, 0, 0.8, 0.9)),
                List.of(new com.peatroxd.streamcutproject.clipcandidate.ClipCandidateWorkerPayload(5.0, 15.0, 0.95, "hello"))
        ));

        ClipCandidate candidate = clipCandidateRepository.findAllByJobIdOrderByScoreDescStartSecAscIdAsc(job.getId()).getFirst();

        mockMvc.perform(post("/api/candidates/{id}/approve", candidate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"));

        mockMvc.perform(post("/api/candidates/{id}/export", candidate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.exportReady").value(false));

        Path artifactPath = storageService.resolveExportedClipPath(job.getId(), candidate.getId(), ".mp4");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, "artifact-bytes");

        vodJobService.ingestWorkerExportResult(
                new WorkerExportResultPayload(job.getId(), candidate.getId(), artifactPath.toString())
        );

        mockMvc.perform(get("/api/exports/{id}", candidate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exportReady").value(true))
                .andExpect(jsonPath("$.artifactPath").value(org.hamcrest.Matchers.containsString("candidate-1.mp4")));

        assertThat(jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(job.getId()))
                .extracting(event -> event.getEventType())
                .contains("JOB_READY_FOR_REVIEW", "EXPORT_STARTED", "EXPORT_COMPLETED");
    }

    private static VodJob newUrlJob(String sourceUrl) {
        VodJob job = new VodJob();
        job.setSourceType("URL");
        job.setSourceUrl(sourceUrl);
        job.setStatus(JobStatus.QUEUED);
        job.setCreatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        return job;
    }
}
