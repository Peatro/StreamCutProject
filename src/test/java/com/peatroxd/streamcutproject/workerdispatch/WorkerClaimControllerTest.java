package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkerClaimControllerTest {

    private VodJobService vodJobService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vodJobService = Mockito.mock(VodJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkerClaimController(vodJobService))
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void returnsClaimedJobPayloadWhenQueuedJobExists() throws Exception {
        when(vodJobService.claimNextQueuedJob(anyString(), anyString())).thenReturn(Optional.of(
                new WorkerDispatchPayload(
                        7L,
                        3L,
                        "ANALYZE",
                        "/data/storage/jobs/7/source/video.mp4",
                        "FILE",
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        mockMvc.perform(post("/api/internal/worker/claims/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId": "worker-1",
                                  "workerRole": "processing"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(7))
                .andExpect(jsonPath("$.processingVersion").value(3))
                .andExpect(jsonPath("$.taskType").value("ANALYZE"))
                .andExpect(jsonPath("$.videoPath").value("/data/storage/jobs/7/source/video.mp4"))
                .andExpect(jsonPath("$.sourceType").value("FILE"));
    }

    @Test
    void returnsNoContentWhenNoQueuedJobExists() throws Exception {
        when(vodJobService.claimNextQueuedJob(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/internal/worker/claims/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId": "worker-1",
                                  "workerRole": "download"
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsBlankWorkerId() throws Exception {
        mockMvc.perform(post("/api/internal/worker/claims/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId": " ",
                                  "workerRole": "download"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankWorkerRole() throws Exception {
        mockMvc.perform(post("/api/internal/worker/claims/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workerId": "worker-1",
                                  "workerRole": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
