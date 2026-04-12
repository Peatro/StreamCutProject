package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.storage.ArtifactResource;
import com.peatroxd.streamcutproject.storage.ArtifactStorageService;
import com.peatroxd.streamcutproject.vodjob.VodJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@RestController
@RequiredArgsConstructor
public class ClipCandidateController {
    private final VodJobService vodJobService;
    private final ArtifactStorageService artifactStorageService;

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

    @GetMapping("/api/jobs/{id}/source/stream")
    public ResponseEntity<StreamingResponseBody> streamSourceVideo(@PathVariable Long id, @RequestHeader HttpHeaders headers) throws Exception {
        Path sourceVideo = vodJobService.getJobSourceVideoPath(id);
        MediaType mediaType = MediaTypeFactory.getMediaType(sourceVideo.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return buildInlineStreamResponse(sourceVideo, mediaType, sourceVideo.getFileName().toString(), headers);
    }

    @GetMapping("/api/exports/{id}/file")
    public ResponseEntity<?> downloadExportArtifact(@PathVariable Long id) throws Exception {
        String reference = vodJobService.getExportArtifactReference(id);
        java.util.Optional<URI> signedUri = artifactStorageService.createSignedGetUri(reference);
        if (signedUri.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(signedUri.get())
                    .build();
        }
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

    private ResponseEntity<StreamingResponseBody> buildInlineStreamResponse(
            Path sourceVideo,
            MediaType mediaType,
            String filename,
            HttpHeaders headers
    ) throws Exception {
        long contentLength = Files.size(sourceVideo);
        String contentDisposition = "inline; filename=\"" + filename + "\"";

        long start = 0L;
        long rangeLength = contentLength;
        HttpStatus status = HttpStatus.OK;

        if (headers.getRange().isEmpty()) {
            StreamingResponseBody body = outputStream -> writeByteRange(sourceVideo, 0L, contentLength, outputStream);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(body);
        } else {
            var range = headers.getRange().get(0);
            start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            rangeLength = end - start + 1;
            status = HttpStatus.PARTIAL_CONTENT;
        }

        long finalStart = start;
        long finalRangeLength = rangeLength;
        long finalEnd = finalStart + finalRangeLength - 1;
        StreamingResponseBody body = outputStream -> writeByteRange(sourceVideo, finalStart, finalRangeLength, outputStream);

        return ResponseEntity.status(status)
                .contentType(mediaType)
                .contentLength(rangeLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + finalStart + "-" + finalEnd + "/" + contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(body);
    }

    private void writeByteRange(Path sourceVideo, long start, long length, java.io.OutputStream outputStream) throws java.io.IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(sourceVideo, StandardOpenOption.READ)) {
            channel.position(start);

            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long remaining = length;
            while (remaining > 0) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining));
                int bytesRead = channel.read(buffer);
                if (bytesRead < 0) {
                    break;
                }
                outputStream.write(buffer.array(), 0, bytesRead);
                remaining -= bytesRead;
            }
        }
    }
}
