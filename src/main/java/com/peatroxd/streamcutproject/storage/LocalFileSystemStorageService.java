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
        Path resolved = storageProperties.getLocalRoot().resolve("jobs").resolve(Long.toString(jobId));
        return PathSafety.requireWithinRoot(storageProperties.getLocalRoot(), resolved, "job root");
    }

    @Override
    public Path resolveSourceVideoPath(long jobId, String originalFilename) {
        Path resolved = resolveJobRoot(jobId)
                .resolve("source")
                .resolve(sanitizeFilename(originalFilename, "source-video"));
        return PathSafety.requireWithinRoot(storageProperties.getLocalRoot(), resolved, "source video path");
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
        Path resolved = resolveJobRoot(jobId)
                .resolve("audio")
                .resolve("audio.wav");
        return PathSafety.requireWithinRoot(storageProperties.getLocalRoot(), resolved, "audio path");
    }

    @Override
    public Path resolveExportedClipPath(long jobId, long candidateId, String extension) {
        Path resolved = resolveJobRoot(jobId)
                .resolve("exports")
                .resolve("candidate-" + candidateId + normalizeExtension(extension));
        return PathSafety.requireWithinRoot(storageProperties.getLocalRoot(), resolved, "export artifact path");
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
