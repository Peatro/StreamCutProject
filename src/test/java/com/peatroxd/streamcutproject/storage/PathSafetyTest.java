package com.peatroxd.streamcutproject.storage;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathSafetyTest {

    @Test
    void acceptsPathWithinConfiguredRoot() {
        Path root = Path.of("/var/lib/streamcut");
        Path candidate = Path.of("/var/lib/streamcut/jobs/42/source/video.mp4");

        assertThat(PathSafety.requireWithinRoot(root, candidate, "source video path"))
                .isEqualTo(candidate.normalize());
    }

    @Test
    void rejectsPathOutsideConfiguredRoot() {
        Path root = Path.of("/var/lib/streamcut");
        Path candidate = Path.of("/tmp/video.mp4");

        assertThatThrownBy(() -> PathSafety.requireWithinRoot(root, candidate, "source video path"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("source video path must stay within the configured storage root");
    }
}
