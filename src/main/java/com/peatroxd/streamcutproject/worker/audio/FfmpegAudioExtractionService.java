package com.peatroxd.streamcutproject.worker.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FfmpegAudioExtractionService {

    private final ProcessRunner processRunner;

    public FfmpegAudioExtractionService() {
        this(new ProcessBuilderProcessRunner());
    }

    public FfmpegAudioExtractionService(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    public AudioExtractionResult extract(AudioExtractionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Path inputVideoPath = requirePath(request.inputVideoPath(), "inputVideoPath");
        Path outputAudioPath = requirePath(request.outputAudioPath(), "outputAudioPath");

        if (!Files.exists(inputVideoPath)) {
            throw new AudioExtractionException("Input video does not exist: " + inputVideoPath, -1, "");
        }

        try {
            Path parent = outputAudioPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            List<String> command = buildCommand(inputVideoPath, outputAudioPath);
            ProcessExecutionResult executionResult = processRunner.run(command);

            if (executionResult.exitCode() != 0) {
                throw new AudioExtractionException(
                        "ffmpeg failed to extract audio from " + inputVideoPath,
                        executionResult.exitCode(),
                        executionResult.stderr()
                );
            }

            if (!Files.exists(outputAudioPath)) {
                throw new AudioExtractionException(
                        "ffmpeg completed successfully but output is missing: " + outputAudioPath,
                        executionResult.exitCode(),
                        executionResult.stderr()
                );
            }

            return new AudioExtractionResult(
                    inputVideoPath,
                    outputAudioPath,
                    true,
                    executionResult.exitCode(),
                    executionResult.stderr()
            );
        } catch (IOException ex) {
            throw new AudioExtractionException(
                    "Failed to prepare audio extraction for " + inputVideoPath + ": " + ex.getMessage(),
                    -1,
                    ex.getMessage()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AudioExtractionException(
                    "Audio extraction interrupted for " + inputVideoPath,
                    -1,
                    ex.getMessage()
            );
        }
    }

    List<String> buildCommand(Path inputVideoPath, Path outputAudioPath) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-i");
        command.add(inputVideoPath.toString());
        command.add("-vn");
        command.add("-acodec");
        command.add("pcm_s16le");
        command.add("-ar");
        command.add("16000");
        command.add("-ac");
        command.add("1");
        command.add(outputAudioPath.toString());
        return command;
    }

    private static Path requirePath(Path path, String name) {
        if (path == null) {
            throw new AudioExtractionException(name + " must not be null", -1, "");
        }
        return path;
    }
}
