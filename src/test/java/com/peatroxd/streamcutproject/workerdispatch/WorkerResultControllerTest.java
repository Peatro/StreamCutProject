package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkerResultControllerTest {

    private VodJobService vodJobService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vodJobService = Mockito.mock(VodJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkerResultController(vodJobService))
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void acceptsWorkerSuccessPayload() throws Exception {
        when(vodJobService.ingestWorkerResult(any())).thenReturn(new WorkerTransportAck(7L, "READY_FOR_REVIEW"));

        mockMvc.perform(post("/api/internal/worker/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": 7,
                                  "durationSec": 120,
                                  "language": "en",
                                  "videoPath": "/data/storage/jobs/7/source/video.mp4",
                                  "audioPath": "/data/storage/jobs/7/audio/audio.wav",
                                  "transcriptSegments": [],
                                  "silenceSegments": [],
                                  "analysisWindows": [],
                                  "clipCandidates": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(7))
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
    }

    @Test
    void acceptsWorkerExportSuccessPayload() throws Exception {
        when(vodJobService.ingestWorkerExportResult(any())).thenReturn(new WorkerTransportAck(7L, "COMPLETED"));

        mockMvc.perform(post("/api/internal/worker/exports/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": 7,
                                  "candidateId": 11,
                                  "artifactPath": "/data/storage/jobs/7/exports/candidate-11.mp4"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(7))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void acceptsWorkerFailurePayload() throws Exception {
        when(vodJobService.reportWorkerFailure(any())).thenReturn(new WorkerTransportAck(7L, "FAILED"));

        mockMvc.perform(post("/api/internal/worker/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": 7,
                                  "failedState": "TRANSCRIBING",
                                  "message": "transcription failed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(7))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void rejectsInvalidFailurePayload() throws Exception {
        mockMvc.perform(post("/api/internal/worker/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": 7,
                                  "failedState": " ",
                                  "message": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
