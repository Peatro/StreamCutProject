package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidatePageResponse;
import com.peatroxd.streamcutproject.config.ApiExceptionHandler;
import com.peatroxd.streamcutproject.vodjob.api.CreateJobByUrlRequest;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.vodjob.api.TranscriptSegmentResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerExecutionResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerTaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VodJobControllerTest {

    private VodJobService vodJobService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vodJobService = Mockito.mock(VodJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new VodJobController(vodJobService))
                .setControllerAdvice(new ApiExceptionHandler("512MB"))
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void createsUrlJobWithNewStatus() throws Exception {
        when(vodJobService.createUrlJob(anyString())).thenReturn(new JobSummaryResponse(
                1L,
                "QUEUED_FOR_DOWNLOAD",
                "URL",
                "https://example.com/video",
                null,
                null,
                null
        ));

        mockMvc.perform(post("/api/jobs/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://example.com/video"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("QUEUED_FOR_DOWNLOAD"))
                .andExpect(jsonPath("$.sourceType").value("URL"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/video"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(vodJobService).createUrlJob(urlCaptor.capture());
        assertThat(urlCaptor.getValue()).isEqualTo("https://example.com/video");
    }

    @Test
    void createsUploadJobWithFileSourceType() throws Exception {
        when(vodJobService.createFileJob(any(MultipartFile.class))).thenReturn(new JobSummaryResponse(
                2L,
                "QUEUED_FOR_DOWNLOAD",
                "FILE",
                null,
                "video.mp4",
                null,
                null
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "test-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/jobs/upload")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.status").value("QUEUED_FOR_DOWNLOAD"))
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.sourceUrl").isEmpty())
                .andExpect(jsonPath("$.originalFilename").value("video.mp4"));

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(vodJobService).createFileJob(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("video.mp4");
    }

    @Test
    void listsJobs() throws Exception {
        when(vodJobService.listJobs()).thenReturn(List.of(
                new JobListItemResponse(
                        1L,
                        "URL",
                        "https://example.com/video",
                        null,
                        "NEW",
                        null,
                        null,
                        null,
                        null,
                        0,
                        "Pending",
                        new WorkerExecutionResponse(
                                21L,
                                "DOWNLOAD",
                                "RUNNING",
                                "download-worker-1",
                                "download",
                                1L,
                                null,
                                Instant.parse("2026-04-05T10:00:10Z"),
                                Instant.parse("2026-04-05T10:00:20Z"),
                                null,
                                null,
                                null
                        ),
                        new WorkerTaskResponse(
                                31L,
                                "DOWNLOAD",
                                "RUNNING",
                                1L,
                                null,
                                Instant.parse("2026-04-05T10:00:05Z"),
                                Instant.parse("2026-04-05T10:00:10Z"),
                                Instant.parse("2026-04-05T10:00:20Z"),
                                null,
                                null
                        )
                )
        ));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].sourceType").value("URL"))
                .andExpect(jsonPath("$[0].sourceUrl").value("https://example.com/video"))
                .andExpect(jsonPath("$[0].latestTask.taskType").value("DOWNLOAD"));
    }

    @Test
    void getsJobDetails() throws Exception {
        when(vodJobService.getJob(1L)).thenReturn(new JobDetailResponse(
                1L,
                "URL",
                "https://example.com/video",
                "video.mp4",
                "NEW",
                Instant.parse("2026-04-05T10:00:00Z"),
                Instant.parse("2026-04-05T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                null,
                null,
                5,
                "Queued for worker processing",
                null,
                new WorkerTaskResponse(
                        41L,
                        "DOWNLOAD",
                        "QUEUED",
                        1L,
                        null,
                        Instant.parse("2026-04-05T10:00:01Z"),
                        null,
                        null,
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.sourceType").value("URL"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/video"))
                .andExpect(jsonPath("$.originalFilename").value("video.mp4"))
                .andExpect(jsonPath("$.latestTask.taskType").value("DOWNLOAD"));
    }

    @Test
    void retriesFailedJob() throws Exception {
        when(vodJobService.retryJob(1L)).thenReturn(jobDetailResponse("QUEUED_FOR_DOWNLOAD"));

        mockMvc.perform(post("/api/jobs/1/retry"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("QUEUED_FOR_DOWNLOAD"));

        verify(vodJobService).retryJob(1L);
    }

    @Test
    void cancelsQueuedJob() throws Exception {
        when(vodJobService.cancelJob(1L)).thenReturn(jobDetailResponse("CANCELED"));

        mockMvc.perform(post("/api/jobs/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELED"));

        verify(vodJobService).cancelJob(1L);
    }

    @Test
    void forceFailsActiveJob() throws Exception {
        when(vodJobService.forceFailJob(1L)).thenReturn(jobDetailResponse("FAILED"));

        mockMvc.perform(post("/api/jobs/1/force-fail"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("FAILED"));

        verify(vodJobService).forceFailJob(1L);
    }

    @Test
    void listsCandidates() throws Exception {
        when(vodJobService.listCandidates(1L)).thenReturn(List.of(
                new ClipCandidateResponse(
                        7L,
                        5.0,
                        12.0,
                        0.91,
                        "A candidate excerpt",
                        "PENDING",
                        null,
                        null,
                        "NOT_REQUESTED",
                        false,
                        null
                )
        ));

        mockMvc.perform(get("/api/jobs/1/candidates"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].moderationStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].transcriptExcerpt").value("A candidate excerpt"));
    }

    @Test
    void listsCandidatesPage() throws Exception {
        when(vodJobService.listCandidatesPage(1L, 2, 20)).thenReturn(new ClipCandidatePageResponse(
                List.of(new ClipCandidateResponse(
                        7L,
                        5.0,
                        12.0,
                        0.91,
                        "A candidate excerpt",
                        "PENDING",
                        null,
                        null,
                        "NOT_REQUESTED",
                        false,
                        null
                )),
                2,
                20,
                390L,
                20,
                true,
                true
        ));

        mockMvc.perform(get("/api/jobs/1/candidates")
                        .param("page", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].moderationStatus").value("PENDING"))
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalItems").value(390))
                .andExpect(jsonPath("$.totalPages").value(20))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void listsTranscriptSegments() throws Exception {
        when(vodJobService.listTranscriptSegments(1L)).thenReturn(List.of(
                new TranscriptSegmentResponse(21L, 1.5, 3.0, "Hello world", 2),
                new TranscriptSegmentResponse(22L, 3.0, 5.0, "More text", 2)
        ));

        mockMvc.perform(get("/api/jobs/1/transcript"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(21))
                .andExpect(jsonPath("$[0].startSec").value(1.5))
                .andExpect(jsonPath("$[0].text").value("Hello world"))
                .andExpect(jsonPath("$[1].id").value(22))
                .andExpect(jsonPath("$[1].startSec").value(3.0));
    }

    @Test
    void listsJobEvents() throws Exception {
        when(vodJobService.listJobEvents(1L)).thenReturn(List.of(
                new JobEventResponse(10L, "JOB_CREATED", "Job created", Instant.parse("2026-04-05T10:00:01Z")),
                new JobEventResponse(11L, "JOB_QUEUED_FOR_DOWNLOAD", "Job queued for download worker", Instant.parse("2026-04-05T10:00:02Z"))
        ));

        mockMvc.perform(get("/api/jobs/1/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].eventType").value("JOB_CREATED"))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].eventType").value("JOB_QUEUED_FOR_DOWNLOAD"));
    }

    @Test
    void listsWorkerExecutions() throws Exception {
        when(vodJobService.listWorkerExecutions(1L)).thenReturn(List.of(
                new WorkerExecutionResponse(
                        21L,
                        "DOWNLOAD",
                        "SUCCEEDED",
                        "download-worker-1",
                        "download",
                        1L,
                        null,
                        Instant.parse("2026-04-05T10:00:10Z"),
                        Instant.parse("2026-04-05T10:00:20Z"),
                        Instant.parse("2026-04-05T10:00:25Z"),
                        null,
                        null
                ),
                new WorkerExecutionResponse(
                        22L,
                        "ANALYZE",
                        "RUNNING",
                        "processing-worker-1",
                        "processing",
                        1L,
                        null,
                        Instant.parse("2026-04-05T10:00:30Z"),
                        Instant.parse("2026-04-05T10:01:00Z"),
                        null,
                        null,
                        "cuda"
                )
        ));

        mockMvc.perform(get("/api/jobs/1/executions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(21))
                .andExpect(jsonPath("$[0].taskType").value("DOWNLOAD"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[1].id").value(22))
                .andExpect(jsonPath("$[1].taskType").value("ANALYZE"))
                .andExpect(jsonPath("$[1].status").value("RUNNING"))
                .andExpect(jsonPath("$[1].whisperDevice").value("cuda"));
    }

    @Test
    void listsWorkerTasks() throws Exception {
        when(vodJobService.listWorkerTasks(1L)).thenReturn(List.of(
                new WorkerTaskResponse(
                        31L,
                        "DOWNLOAD",
                        "SUCCEEDED",
                        1L,
                        null,
                        Instant.parse("2026-04-05T10:00:10Z"),
                        Instant.parse("2026-04-05T10:00:12Z"),
                        Instant.parse("2026-04-05T10:00:15Z"),
                        Instant.parse("2026-04-05T10:00:20Z"),
                        null
                ),
                new WorkerTaskResponse(
                        32L,
                        "ANALYZE",
                        "RUNNING",
                        1L,
                        null,
                        Instant.parse("2026-04-05T10:00:30Z"),
                        Instant.parse("2026-04-05T10:00:31Z"),
                        Instant.parse("2026-04-05T10:01:00Z"),
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/api/jobs/1/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(31))
                .andExpect(jsonPath("$[0].taskType").value("DOWNLOAD"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[1].id").value(32))
                .andExpect(jsonPath("$[1].taskType").value("ANALYZE"))
                .andExpect(jsonPath("$[1].status").value("RUNNING"));
    }

    @Test
    void rejectsBlankUrl() throws Exception {
        mockMvc.perform(post("/api/jobs/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingUploadFile() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/jobs/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Upload file is required."))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/jobs/upload"));
    }

    private static JobDetailResponse jobDetailResponse(String status) {
        return new JobDetailResponse(
                1L,
                "URL",
                "https://example.com/video",
                "video.mp4",
                status,
                Instant.parse("2026-04-05T10:00:00Z"),
                Instant.parse("2026-04-05T10:01:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                null,
                null,
                5,
                "Queued for download worker",
                null,
                null
        );
    }
}
