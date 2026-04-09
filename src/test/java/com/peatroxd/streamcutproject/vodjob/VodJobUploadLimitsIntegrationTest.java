package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamcut-upload-limits;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=4KB"
})
@Transactional
class VodJobUploadLimitsIntegrationTest {

    private static final Path STORAGE_ROOT;

    static {
        try {
            STORAGE_ROOT = Files.createTempDirectory("streamcut-upload-limits-storage");
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

    private MockMvc mockMvc;

    @Autowired
    private VodJobRepository vodJobRepository;

    @Autowired
    private JobEventRepository jobEventRepository;

    @Autowired
    private MultipartProperties multipartProperties;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void acceptsUploadWithinConfiguredLimit() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "clip.mp4",
                "video/mp4",
                new byte[512]
        );

        mockMvc.perform(multipart("/api/jobs/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.originalFilename").value("clip.mp4"))
                .andExpect(jsonPath("$.status").value("QUEUED_FOR_DOWNLOAD"));

        VodJob savedJob = vodJobRepository.findAll().stream().findFirst().orElseThrow();
        assertThat(savedJob.getStorageVideoPath()).isNotBlank();
        assertThat(Path.of(savedJob.getStorageVideoPath())).exists();
        assertThat(jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(savedJob.getId()))
                .extracting(event -> event.getEventType())
                .containsExactly("JOB_CREATED", "JOB_QUEUED_FOR_DOWNLOAD");
    }

    @Test
    void bindsConfiguredMultipartLimits() {
        assertThat(multipartProperties.getMaxFileSize().toBytes()).isEqualTo(1024);
        assertThat(multipartProperties.getMaxRequestSize().toBytes()).isEqualTo(4096);
    }
}
