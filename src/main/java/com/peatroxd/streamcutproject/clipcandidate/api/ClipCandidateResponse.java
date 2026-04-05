package com.peatroxd.streamcutproject.clipcandidate.api;

public record ClipCandidateResponse(
        Long id,
        Double startSec,
        Double endSec,
        Double score,
        String transcriptExcerpt,
        String moderationStatus,
        String moderatorNote,
        String exportedClipPath
) {
}
