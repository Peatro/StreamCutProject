package com.peatroxd.streamcutproject.storage;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileSystemStorageServiceTest {

    private final StorageProperties storageProperties = new StorageProperties();
    private final LocalFileSystemStorageService storageService = new LocalFileSystemStorageService(storageProperties);

    @Test
    void resolvesStableJobRoot() {
        storageProperties.setLocalRoot(Path.of("/var/lib/streamcut"));

        assertThat(storageService.resolveJobRoot(42L))
                .isEqualTo(Path.of("/var/lib/streamcut/jobs/42"));
    }

    @Test
    void resolvesSourceVideoPathUsingSafeFilename() {
        storageProperties.setLocalRoot(Path.of("/var/lib/streamcut"));

        assertThat(storageService.resolveSourceVideoPath(42L, "../my clip.mp4"))
                .isEqualTo(Path.of("/var/lib/streamcut/jobs/42/source/my_clip.mp4"));
    }

    @Test
    void fallsBackForInvalidSourceFilename() {
        storageProperties.setLocalRoot(Path.of("/var/lib/streamcut"));

        assertThat(storageService.resolveSourceVideoPath(42L, "\u0000"))
                .isEqualTo(Path.of("/var/lib/streamcut/jobs/42/source/source-video.bin"));
    }

    @Test
    void resolvesAudioPathWithFixedName() {
        storageProperties.setLocalRoot(Path.of("/var/lib/streamcut"));

        assertThat(storageService.resolveAudioPath(42L))
                .isEqualTo(Path.of("/var/lib/streamcut/jobs/42/audio/audio.wav"));
    }

    @Test
    void resolvesExportedClipPathWithConfiguredExtension() {
        storageProperties.setLocalRoot(Path.of("/var/lib/streamcut"));

        assertThat(storageService.resolveExportedClipPath(42L, 7L, "mkv"))
                .isEqualTo(Path.of("/var/lib/streamcut/jobs/42/exports/candidate-7.mkv"));
    }

    @Test
    void storesUploadedSourceVideoInResolvedLocation(@TempDir Path tempDir) throws IOException {
        storageProperties.setLocalRoot(tempDir);

        Path storedPath = storageService.storeSourceVideo(
                42L,
                "../my clip.mp4",
                new ByteArrayInputStream("video-bytes".getBytes())
        );

        assertThat(storedPath)
                .isEqualTo(tempDir.resolve("jobs/42/source/my_clip.mp4"));
        assertThat(Files.readString(storedPath))
                .isEqualTo("video-bytes");
    }
}
