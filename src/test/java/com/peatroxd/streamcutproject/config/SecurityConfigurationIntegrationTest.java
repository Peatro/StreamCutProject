package com.peatroxd.streamcutproject.config;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamcut-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class SecurityConfigurationIntegrationTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        VodJobService vodJobService() {
            return Mockito.mock(VodJobService.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private VodJobService vodJobService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void redirectsUnauthenticatedBrowserAccessToLoginPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void returnsStableJsonErrorForUnauthenticatedApiAccess() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required."))
                .andExpect(jsonPath("$.path").value("/api/jobs"));
    }

    @Test
    void redirectsUnauthenticatedActuatorAccessToLoginPage() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void exposesCsrfBootstrapPublicly() throws Exception {
        mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void exposesWorkerHealthEndpointsPublicly() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(get("/health/workers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.roles.download.role").value("download"))
                .andExpect(jsonPath("$.roles.processing.role").value("processing"));
    }

    @Test
    void authenticatesOperatorAndAllowsProtectedApiAccess() throws Exception {
        when(vodJobService.listJobs()).thenReturn(List.of(
                new JobListItemResponse(
                        1L,
                        "URL",
                        "https://example.com/video",
                        null,
                        "QUEUED_FOR_DOWNLOAD",
                        Instant.parse("2026-04-06T10:00:00Z"),
                        Instant.parse("2026-04-06T10:00:05Z"),
                        null,
                        null,
                        5,
                        "Queued for download worker",
                        null,
                        null
                )
        ));
        when(vodJobService.createUrlJob(anyString())).thenReturn(
                new JobSummaryResponse(2L, "QUEUED_FOR_DOWNLOAD", "URL", "https://example.com/video", null, null, null)
        );

        MockHttpSession session = operatorSession();

        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/jobs").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1));

        mockMvc.perform(post("/api/jobs/url")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://example.com/video"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void authenticatesOperatorAndAllowsProtectedActuatorAccess() throws Exception {
        MockHttpSession session = operatorSession();

        mockMvc.perform(get("/actuator/metrics").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItem("streamcut.queue.depth")))
                .andExpect(jsonPath("$.names", hasItem("streamcut.jobs.active")));

        mockMvc.perform(get("/actuator/prometheus").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("streamcut_queue_depth")))
                .andExpect(content().string(containsString("streamcut_jobs_active")));
    }

    @Test
    void allowsUnauthenticatedWorkerPostWithoutCsrf() throws Exception {
        when(vodJobService.claimNextQueuedJob("worker-1", "processing")).thenReturn(
                java.util.Optional.of(new WorkerDispatchPayload(
                        11L,
                        7L,
                        2L,
                        "ANALYZE",
                        "/data/storage/jobs/7/source/video.mp4",
                        "FILE",
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

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
                .andExpect(jsonPath("$.taskType").value("ANALYZE"));
    }

    private MockHttpSession operatorSession() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "operator")
                        .param("password", "operator-password"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/index.html"))
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
