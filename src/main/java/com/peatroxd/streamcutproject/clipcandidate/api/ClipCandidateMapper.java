package com.peatroxd.streamcutproject.clipcandidate.api;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;

public final class ClipCandidateMapper {

    private ClipCandidateMapper() {
    }

    public static ClipCandidateResponse toResponse(ClipCandidate candidate, boolean exportReady) {
        String exportedClipPath = candidate.getExportedClipPath();
        return new ClipCandidateResponse(
                candidate.getId(),
                candidate.getStartSec(),
                candidate.getEndSec(),
                candidate.getScore(),
                candidate.getTranscriptExcerpt(),
                candidate.getModerationStatus().name(),
                candidate.getModeratorNote(),
                exportedClipPath,
                candidate.getExportStatus().name(),
                exportReady
        );
    }
}
