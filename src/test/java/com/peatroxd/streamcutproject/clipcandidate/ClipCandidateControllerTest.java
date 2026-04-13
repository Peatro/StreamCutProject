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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private ClipCandidateController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vodJobService = Mockito.mock(VodJobService.class);
        artifactStorageService = Mockito.mock(ArtifactStorageService.class);
        controller = new ClipCandidateController(vodJobService, artifactStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
                false,
                null
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
                false,
                null
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
                "IN_PROGRESS",
                "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4",
                "PENDING",
                false,
                null
        ));

        mockMvc.perform(post("/api/candidates/7/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.artifactPath").value("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
    }

    @Test
    void getsExportStatus() throws Exception {
        when(vodJobService.getExportStatus(anyLong())).thenReturn(new ExportStatusResponse(
                7L,
                1L,
                "IN_PROGRESS",
                "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4",
                "PENDING",
                false,
                null
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/exports/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.artifactPath").value("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
    }

    @Test
    void streamsSourceVideoInline(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source-video.mp4");
        Files.writeString(source, "video");
        when(vodJobService.getJobSourceVideoPath(anyLong())).thenReturn(source);

        ResponseEntity<StreamingResponseBody> response = controller.streamSourceVideo(1L, new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline; filename=\"source-video.mp4\"");
        assertThat(response.getBody()).isNotNull();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        response.getBody().writeTo(outputStream);
        assertThat(outputStream.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("video");
    }

    @Test
    void streamsSourceVideoRangeRequest(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source-video.mp4");
        Files.writeString(source, "video");
        when(vodJobService.getJobSourceVideoPath(anyLong())).thenReturn(source);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RANGE, "bytes=1-3");

        ResponseEntity<StreamingResponseBody> response = controller.streamSourceVideo(1L, headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 1-3/5");
        assertThat(response.getBody()).isNotNull();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        response.getBody().writeTo(outputStream);
        assertThat(outputStream.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("ide");
    }

    @Test
    void downloadsWorkerSourceVideoThroughInternalEndpoint(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source-video.mp4");
        Files.writeString(source, "video");
        when(vodJobService.getSourceVideoReference(anyLong())).thenReturn(source.toString());
        when(artifactStorageService.createSignedGetUri(source.toString())).thenReturn(Optional.empty());
        when(artifactStorageService.open(source.toString())).thenReturn(new ArtifactResource(
                Files.newInputStream(source),
                Files.size(source),
                "video/mp4",
                source.getFileName().toString()
        ));

        mockMvc.perform(get("/api/internal/worker/jobs/1/source/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"source-video.mp4\""));
    }

    @Test
    void downloadsExportArtifact(@TempDir Path tempDir) throws Exception {
        Path artifact = tempDir.resolve("candidate-7.mp4");
        Files.writeString(artifact, "video");
        when(vodJobService.getExportArtifactReference(anyLong())).thenReturn(artifact.toString());
        when(artifactStorageService.createSignedGetUri(artifact.toString())).thenReturn(Optional.empty());
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
    void redirectsExportDownloadToSignedUrlWhenAvailable() throws Exception {
        String reference = "s3://streamcut-artifacts/exports/jobs/1/candidate-7.mp4";
        URI signedUri = URI.create("http://localhost:9000/streamcut-artifacts/exports/jobs/1/candidate-7.mp4?X-Amz-Signature=test");
        when(vodJobService.getExportArtifactReference(anyLong())).thenReturn(reference);
        when(artifactStorageService.createSignedGetUri(reference)).thenReturn(Optional.of(signedUri));

        mockMvc.perform(get("/api/exports/7/file"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, signedUri.toString()));
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
        when(artifactStorageService.createSignedGetUri(reference)).thenReturn(Optional.empty());
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
