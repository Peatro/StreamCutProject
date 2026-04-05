package com.peatroxd.streamcutproject.worker.audio;

import java.nio.file.Path;

public record AudioExtractionRequest(
        Path inputVideoPath,
        Path outputAudioPath
) {
}
