package com.peatroxd.streamcutproject.transcript;

import java.util.List;

public record TranscriptSegmentWorkerPayload(
        Double startSec,
        Double endSec,
        String text,
        Integer wordCount,
        List<TranscriptWordPayload> words
) {
}
