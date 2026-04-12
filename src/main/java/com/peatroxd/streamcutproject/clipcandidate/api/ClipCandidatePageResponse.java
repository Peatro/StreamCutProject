package com.peatroxd.streamcutproject.clipcandidate.api;

import java.util.List;

public record ClipCandidatePageResponse(
        List<ClipCandidateResponse> items,
        int pageNumber,
        int pageSize,
        long totalItems,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
}
