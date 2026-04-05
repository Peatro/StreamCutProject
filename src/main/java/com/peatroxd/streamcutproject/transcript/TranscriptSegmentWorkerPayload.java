package com.peatroxd.streamcutproject.transcript;

public record TranscriptSegmentWorkerPayload(
        Double startSec,
        Double endSec,
        String text,
        Integer wordCount
) {
}
