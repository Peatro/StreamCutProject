package com.peatroxd.streamcutproject.clipcandidate;

public record ClipCandidateWorkerPayload(
        Double startSec,
        Double endSec,
        Double score,
        String transcriptExcerpt
) {
}
