package com.peatroxd.streamcutproject.analysiswindow;

public record AnalysisWindowWorkerPayload(
        Double startSec,
        Double endSec,
        Double speechDensity,
        Double silenceRatio,
        Integer emotionHits,
        Double continuityScore,
        Double totalScore
) {
}
