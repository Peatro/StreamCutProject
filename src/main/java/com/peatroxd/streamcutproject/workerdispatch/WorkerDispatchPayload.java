package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.transcript.TranscriptWordPayload;

import java.util.List;

public record WorkerDispatchPayload(
        Long executionId,
        Long jobId,
        Long processingVersion,
        String taskType,
        String videoPath,
        String videoReference,
        String videoDownloadUrl,
        String sourceType,
        String sourceUrl,
        Long candidateId,
        Double clipStartSec,
        Double clipEndSec,
        String artifactPath,
        List<TranscriptWordPayload> clipWords
) {
}
