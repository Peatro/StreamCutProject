package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.storage.ArtifactResource;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class ClipCandidateController {
    private final VodJobService vodJobService;
    private final ArtifactStorageService artifactStorageService;

    public ClipCandidateController(VodJobService vodJobService, ArtifactStorageService artifactStorageService) {
        this.vodJobService = vodJobService;
        this.artifactStorageService = artifactStorageService;
    }

    @PostMapping("/api/candidates/{id}/approve")
    public ClipCandidateResponse approveCandidate(@PathVariable Long id) {
        return vodJobService.approveCandidate(id);
    }

    @PostMapping("/api/candidates/{id}/reject")
    public ClipCandidateResponse rejectCandidate(@PathVariable Long id) {
        return vodJobService.rejectCandidate(id);
    }

    @PostMapping("/api/candidates/{id}/export")
    public ExportStatusResponse exportCandidate(@PathVariable Long id) {
        return vodJobService.startExport(id);
    }

    @GetMapping("/api/exports/{id}")
    public ExportStatusResponse getExportStatus(@PathVariable Long id) {
        return vodJobService.getExportStatus(id);
    }

    @GetMapping("/api/exports/{id}/file")
    public ResponseEntity<Resource> downloadExportArtifact(@PathVariable Long id) throws Exception {
        String reference = vodJobService.getExportArtifactReference(id);
        ArtifactResource artifact = artifactStorageService.open(reference);
        InputStreamResource resource = new InputStreamResource(artifact.inputStream());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(artifact.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .body(resource);
    }

    @GetMapping("/api/exports/{id}/stream")
    public ResponseEntity<Resource> streamExportArtifact(@PathVariable Long id) throws Exception {
        String reference = vodJobService.getExportArtifactReference(id);
        ArtifactResource artifact = artifactStorageService.open(reference);
        InputStreamResource resource = new InputStreamResource(artifact.inputStream());
        MediaType mediaType = MediaTypeFactory.getMediaType(artifact.filename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(artifact.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + artifact.filename() + "\"")
                .body(resource);
    }
}
