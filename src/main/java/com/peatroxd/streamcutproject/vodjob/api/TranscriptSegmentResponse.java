package com.peatroxd.streamcutproject.vodjob.api;

import com.peatroxd.streamcutproject.transcript.TranscriptWordPayload;

import java.util.List;

public record TranscriptSegmentResponse(
        Long id,
        Double startSec,
        Double endSec,
        String text,
        Integer wordCount,
        List<TranscriptWordPayload> words
) {
}
