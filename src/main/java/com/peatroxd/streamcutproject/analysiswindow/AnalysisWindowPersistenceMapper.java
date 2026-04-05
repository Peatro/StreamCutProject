package com.peatroxd.streamcutproject.analysiswindow;

import com.peatroxd.streamcutproject.vodjob.VodJob;

import java.util.List;

public final class AnalysisWindowPersistenceMapper {

    private AnalysisWindowPersistenceMapper() {
    }

    public static AnalysisWindowWorkerPayload payload(
            Double startSec,
            Double endSec,
            Double speechDensity,
            Double silenceRatio,
            Integer emotionHits,
            Double continuityScore,
            Double totalScore
    ) {
        return new AnalysisWindowWorkerPayload(
                startSec,
                endSec,
                speechDensity,
                silenceRatio,
                emotionHits,
                continuityScore,
                totalScore
        );
    }

    public static AnalysisWindow toEntity(VodJob vodJob, AnalysisWindowWorkerPayload payload) {
        return AnalysisWindow.create(
                vodJob,
                payload.startSec(),
                payload.endSec(),
                payload.speechDensity(),
                payload.silenceRatio(),
                payload.emotionHits(),
                payload.continuityScore(),
                payload.totalScore()
        );
    }

    public static List<AnalysisWindow> toEntities(VodJob vodJob, List<AnalysisWindowWorkerPayload> payloads) {
        return payloads.stream()
                .map(payload -> toEntity(vodJob, payload))
                .toList();
    }
}
