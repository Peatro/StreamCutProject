package com.peatroxd.streamcutproject.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.artifact-storage", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalArtifactStorageService implements ArtifactStorageService {

    @Override
    public String storeCompletedExport(long jobId, long candidateId, Path localArtifactPath) {
        return normalizeReference(localArtifactPath);
    }

    @Override
    public boolean exists(String reference) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        return resolveLocalPath(reference)
                .map(Files::exists)
                .orElse(false);
    }

    @Override
    public void delete(String reference) throws IOException {
        Path path = resolveLocalPath(reference)
                .orElseThrow(() -> new IOException("Artifact reference is not available as a local path: " + reference));
        Files.deleteIfExists(path);
    }

    @Override
    public Optional<Path> resolveLocalPath(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(reference).normalize());
    }

    @Override
    public Optional<URI> createSignedGetUri(String reference) {
        return Optional.empty();
    }

    @Override
    public ArtifactResource open(String reference) throws IOException {
        Path path = resolveLocalPath(reference)
                .orElseThrow(() -> new IOException("Artifact reference is not available as a local path: " + reference));
        return new ArtifactResource(
                Files.newInputStream(path),
                Files.size(path),
                Files.probeContentType(path),
                path.getFileName().toString()
        );
    }

    private static String normalizeReference(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }
}
