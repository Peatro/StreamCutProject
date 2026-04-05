package com.peatroxd.streamcutproject.worker.audio;

public record ProcessExecutionResult(
        int exitCode,
        String stdout,
        String stderr
) {
}
