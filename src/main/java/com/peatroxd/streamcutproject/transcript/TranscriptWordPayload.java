package com.peatroxd.streamcutproject.transcript;

public record TranscriptWordPayload(
        String word,
        Double startSec,
        Double endSec
) {
}
