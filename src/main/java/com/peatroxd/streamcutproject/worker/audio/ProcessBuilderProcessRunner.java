package com.peatroxd.streamcutproject.worker.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ProcessBuilderProcessRunner implements ProcessRunner {

    @Override
    public ProcessExecutionResult run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();

        AtomicReference<String> stdout = new AtomicReference<>("");
        AtomicReference<String> stderr = new AtomicReference<>("");

        Thread stdoutReader = Thread.startVirtualThread(() -> stdout.set(drain(process.getInputStream())));
        Thread stderrReader = Thread.startVirtualThread(() -> stderr.set(drain(process.getErrorStream())));
        int exitCode = process.waitFor();

        stdoutReader.join();
        stderrReader.join();

        return new ProcessExecutionResult(
                exitCode,
                stdout.get(),
                stderr.get()
        );
    }

    private static String drain(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read process output", ex);
        }
    }
}
