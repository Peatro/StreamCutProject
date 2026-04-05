package com.peatroxd.streamcutproject.worker.audio;

import java.nio.file.Path;

public record AudioExtractionResult(
        Path inputVideoPath,
        Path outputAudioPath,
        boolean success,
        int exitCode,
        String stderr
) {
}
