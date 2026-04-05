package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.vodjob.api.CreateJobByUrlRequest;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.vodjob.api.TranscriptSegmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void createsUrlJobWithNewStatus() throws Exception {
        when(vodJobService.createUrlJob(anyString())).thenReturn(new JobSummaryResponse(
                1L,
                "NEW",
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
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.sourceType").value("URL"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/video"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(vodJobService).createUrlJob(urlCaptor.capture());
        assertThat(urlCaptor.getValue()).isEqualTo("https://example.com/video");
    }

    @Test
    void createsUploadJobWithFileSourceType() throws Exception {
        when(vodJobService.createFileJob("video.mp4")).thenReturn(new JobSummaryResponse(
                2L,
                "NEW",
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
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.sourceUrl").isEmpty())
                .andExpect(jsonPath("$.originalFilename").value("video.mp4"));

        ArgumentCaptor<String> originalFilenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(vodJobService).createFileJob(originalFilenameCaptor.capture());
        assertThat(originalFilenameCaptor.getValue()).isEqualTo("video.mp4");
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
                        null
                )
        ));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].sourceType").value("URL"))
                .andExpect(jsonPath("$[0].sourceUrl").value("https://example.com/video"));
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
                null
        ));

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.sourceType").value("URL"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/video"))
                .andExpect(jsonPath("$.originalFilename").value("video.mp4"));
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
                new JobEventResponse(11L, "JOB_QUEUED", "Job queued", Instant.parse("2026-04-05T10:00:02Z"))
        ));

        mockMvc.perform(get("/api/jobs/1/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].eventType").value("JOB_CREATED"))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].eventType").value("JOB_QUEUED"));
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
                .andExpect(status().isBadRequest());
    }
}
