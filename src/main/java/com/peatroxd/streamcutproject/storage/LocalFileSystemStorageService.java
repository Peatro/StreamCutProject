package com.peatroxd.streamcutproject.storage;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class LocalFileSystemStorageService implements StorageService {

    private final StorageProperties storageProperties;

    public LocalFileSystemStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public Path resolveJobRoot(long jobId) {
        return storageProperties.getLocalRoot().resolve("jobs").resolve(Long.toString(jobId));
    }

    @Override
    public Path resolveSourceVideoPath(long jobId, String originalFilename) {
        return resolveJobRoot(jobId)
                .resolve("source")
                .resolve(sanitizeFilename(originalFilename, "source-video"));
    }

    @Override
    public Path storeSourceVideo(long jobId, String originalFilename, InputStream content) throws IOException {
        Path targetPath = resolveSourceVideoPath(jobId, originalFilename);
        Files.createDirectories(targetPath.getParent());
        Files.copy(content, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath;
    }

    @Override
    public Path resolveAudioPath(long jobId) {
        return resolveJobRoot(jobId)
                .resolve("audio")
                .resolve("audio.wav");
    }

    @Override
    public Path resolveExportedClipPath(long jobId, long candidateId, String extension) {
        return resolveJobRoot(jobId)
                .resolve("exports")
                .resolve("candidate-" + candidateId + normalizeExtension(extension));
    }

    private static String sanitizeFilename(String filename, String fallbackBaseName) {
        String safeName;
        try {
            safeName = filename == null ? "" : Paths.get(filename).getFileName().toString();
        } catch (InvalidPathException ex) {
            safeName = "";
        }
        if (safeName.isBlank()) {
            return fallbackBaseName + ".bin";
        }

        int lastDot = safeName.lastIndexOf('.');
        String baseName = lastDot > 0 ? safeName.substring(0, lastDot) : safeName;
        String extension = lastDot > 0 ? safeName.substring(lastDot) : ".bin";

        String normalizedBaseName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (normalizedBaseName.isBlank()) {
            normalizedBaseName = fallbackBaseName;
        }

        String normalizedExtension = extension.replaceAll("[^a-zA-Z0-9.]", "");
        if (normalizedExtension.isBlank() || !normalizedExtension.startsWith(".")) {
            normalizedExtension = ".bin";
        }

        return normalizedBaseName + normalizedExtension;
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".mp4";
        }

        String normalized = extension.startsWith(".") ? extension : "." + extension;
        normalized = normalized.replaceAll("[^a-zA-Z0-9.]", "");
        if (normalized.isBlank() || !normalized.startsWith(".")) {
            return ".mp4";
        }
        return normalized;
    }
}
