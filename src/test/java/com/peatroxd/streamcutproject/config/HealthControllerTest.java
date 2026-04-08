package com.peatroxd.streamcutproject.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(new FakeWorkerDiagnosticsService())).build();

    @Test
    void returnsUpStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void returnsReadyStatus() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void returnsWorkerDiagnostics() throws Exception {
        mockMvc.perform(get("/health/workers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.staleTimeoutSec").value(120))
                .andExpect(jsonPath("$.reconcileIntervalSec").value(30))
                .andExpect(jsonPath("$.roles.download.role").value("download"))
                .andExpect(jsonPath("$.roles.download.queued").value(1))
                .andExpect(jsonPath("$.roles.processing.running").value(1))
                .andExpect(jsonPath("$.roles.processing.activeByTaskType.ANALYZE").value(1));
    }

    private static final class FakeWorkerDiagnosticsService extends WorkerDiagnosticsService {
        private FakeWorkerDiagnosticsService() {
            super(null, null);
        }

        @Override
        public WorkerDiagnosticsResponse snapshot() {
            return new WorkerDiagnosticsResponse(
                    "UP",
                    120,
                    30,
                    Map.of(
                            "download",
                            new WorkerRoleDiagnostics(
                                    "download",
                                    1,
                                    0,
                                    0,
                                    0,
                                    null,
                                    Map.of(com.peatroxd.streamcutproject.workerexecution.WorkerTaskType.DOWNLOAD, 1),
                                    Map.of()
                            ),
                            "processing",
                            new WorkerRoleDiagnostics(
                                    "processing",
                                    0,
                                    0,
                                    1,
                                    0,
                                    Instant.parse("2026-04-08T18:00:00Z"),
                                    Map.of(),
                                    Map.of(com.peatroxd.streamcutproject.workerexecution.WorkerTaskType.ANALYZE, 1)
                            )
                    )
            );
        }
    }
}
