package com.peatroxd.streamcutproject.silence;

public record SilenceSegmentWorkerPayload(
        Double startSec,
        Double endSec,
        Double durationSec
) {
}
