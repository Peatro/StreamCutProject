package com.peatroxd.streamcutproject.vodjob.api;

public record TranscriptSegmentResponse(
        Long id,
        Double startSec,
        Double endSec,
        String text,
        Integer wordCount
) {
}
