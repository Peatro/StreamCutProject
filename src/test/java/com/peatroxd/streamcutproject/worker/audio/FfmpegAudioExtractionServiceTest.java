package com.peatroxd.streamcutproject.worker.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegAudioExtractionServiceTest {

    @Test
    void buildsExpectedCommand() {
        FfmpegAudioExtractionService service = new FfmpegAudioExtractionService((command) -> {
            throw new UnsupportedOperationException("not used");
        });

        Path inputVideo = Path.of("input", "video.mp4");
        Path outputAudio = Path.of("output", "audio.wav");
        List<String> command = service.buildCommand(inputVideo, outputAudio);

        assertThat(command).containsExactly(
                "ffmpeg",
                "-y",
                "-i",
                inputVideo.toString(),
                "-vn",
                "-acodec",
                "pcm_s16le",
                "-ar",
                "16000",
                "-ac",
                "1",
                outputAudio.toString()
        );
    }

    @Test
    void returnsStructuredResultWhenProcessSucceeds() throws IOException {
        Path tempDir = Files.createTempDirectory("audio-extraction-success");
        Path inputVideo = Files.createTempFile(tempDir, "input", ".mp4");
        Path outputAudio = tempDir.resolve("out/audio.wav");

        ProcessRunner runner = command -> {
            Files.createDirectories(outputAudio.getParent());
            Files.writeString(outputAudio, "audio");
            return new ProcessExecutionResult(0, "", "");
        };

        FfmpegAudioExtractionService service = new FfmpegAudioExtractionService(runner);

        AudioExtractionResult result = service.extract(new AudioExtractionRequest(inputVideo, outputAudio));

        assertThat(result.success()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.outputAudioPath()).isEqualTo(outputAudio);
        assertThat(Files.exists(outputAudio)).isTrue();
    }

    @Test
    void surfacesFfmpegFailures() throws IOException {
        Path tempDir = Files.createTempDirectory("audio-extraction-failure");
        Path inputVideo = Files.createTempFile(tempDir, "input", ".mp4");
        Path outputAudio = tempDir.resolve("out/audio.wav");

        ProcessRunner runner = command -> new ProcessExecutionResult(1, "", "boom");
        FfmpegAudioExtractionService service = new FfmpegAudioExtractionService(runner);

        assertThatThrownBy(() -> service.extract(new AudioExtractionRequest(inputVideo, outputAudio)))
                .isInstanceOf(AudioExtractionException.class)
                .hasMessageContaining("ffmpeg failed")
                .matches(ex -> ((AudioExtractionException) ex).getExitCode() == 1);
    }

    @Test
    void failsFastWhenInputDoesNotExist() {
        FfmpegAudioExtractionService service = new FfmpegAudioExtractionService(command -> {
            throw new UnsupportedOperationException("not used");
        });

        assertThatThrownBy(() -> service.extract(
                new AudioExtractionRequest(Path.of("does-not-exist.mp4"), Path.of("out/audio.wav"))))
                .isInstanceOf(AudioExtractionException.class)
                .hasMessageContaining("Input video does not exist");
    }
}
