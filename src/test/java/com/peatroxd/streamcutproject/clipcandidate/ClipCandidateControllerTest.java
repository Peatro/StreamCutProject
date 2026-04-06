package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.storage.ArtifactResource;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClipCandidateControllerTest {

    private VodJobService vodJobService;
    private ArtifactStorageService artifactStorageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vodJobService = Mockito.mock(VodJobService.class);
        artifactStorageService = Mockito.mock(ArtifactStorageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ClipCandidateController(vodJobService, artifactStorageService)).build();
    }

    @Test
    void approvesCandidate() throws Exception {
        when(vodJobService.approveCandidate(anyLong())).thenReturn(new ClipCandidateResponse(
                7L,
                5.0,
                12.0,
                0.91,
                "A candidate excerpt",
                "APPROVED",
                null,
                null,
                "NOT_REQUESTED",
                false
        ));

        mockMvc.perform(post("/api/candidates/7/approve"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"));
    }

    @Test
    void rejectsCandidate() throws Exception {
        when(vodJobService.rejectCandidate(anyLong())).thenReturn(new ClipCandidateResponse(
                7L,
                5.0,
                12.0,
                0.91,
                "A candidate excerpt",
                "REJECTED",
                null,
                null,
                "NOT_REQUESTED",
                false
        ));

        mockMvc.perform(post("/api/candidates/7/reject"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"));
    }

    @Test
    void startsExportForApprovedCandidate() throws Exception {
        when(vodJobService.startExport(anyLong())).thenReturn(new ExportStatusResponse(
                7L,
                1L,
                "EXPORTING_CLIP",
                "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4",
                "APPROVED",
                false
        ));

        mockMvc.perform(post("/api/candidates/7/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("EXPORTING_CLIP"))
                .andExpect(jsonPath("$.artifactPath").value("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
    }

    @Test
    void getsExportStatus() throws Exception {
        when(vodJobService.getExportStatus(anyLong())).thenReturn(new ExportStatusResponse(
                7L,
                1L,
                "EXPORTING_CLIP",
                "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4",
                "APPROVED",
                false
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/exports/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("EXPORTING_CLIP"))
                .andExpect(jsonPath("$.artifactPath").value("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
    }

    @Test
    void downloadsExportArtifact(@TempDir Path tempDir) throws Exception {
        Path artifact = tempDir.resolve("candidate-7.mp4");
        Files.writeString(artifact, "video");
        when(vodJobService.getExportArtifactReference(anyLong())).thenReturn(artifact.toString());
        when(artifactStorageService.open(artifact.toString())).thenReturn(new ArtifactResource(
                Files.newInputStream(artifact),
                Files.size(artifact),
                "video/mp4",
                artifact.getFileName().toString()
        ));

        mockMvc.perform(get("/api/exports/7/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"candidate-7.mp4\""));
    }

    @Test
    void streamsExportArtifactInline(@TempDir Path tempDir) throws Exception {
        Path artifact = tempDir.resolve("candidate-7.mp4");
        Files.writeString(artifact, "video");
        when(vodJobService.getExportArtifactReference(anyLong())).thenReturn(artifact.toString());
        when(artifactStorageService.open(artifact.toString())).thenReturn(new ArtifactResource(
                Files.newInputStream(artifact),
                Files.size(artifact),
                "video/mp4",
                artifact.getFileName().toString()
        ));

        mockMvc.perform(get("/api/exports/7/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"candidate-7.mp4\""));
    }

    @Test
    void downloadsArtifactStoredInObjectStorageWithoutRedirect() throws Exception {
        String reference = "s3://streamcut-artifacts/exports/jobs/1/candidate-7.mp4";
        when(vodJobService.getExportArtifactReference(anyLong())).thenReturn(reference);
        when(artifactStorageService.open(reference)).thenReturn(new ArtifactResource(
                new java.io.ByteArrayInputStream("video".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                5L,
                "video/mp4",
                "candidate-7.mp4"
        ));

        mockMvc.perform(get("/api/exports/7/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"candidate-7.mp4\""));
    }
}
