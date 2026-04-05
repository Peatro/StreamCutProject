package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.vodjob.api.CreateJobByUrlRequest;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.time.Instant;

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
}
