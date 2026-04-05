package com.peatroxd.streamcutproject.silence;

import com.peatroxd.streamcutproject.vodjob.VodJob;

import java.util.List;

public final class SilenceSegmentPersistenceMapper {

    private SilenceSegmentPersistenceMapper() {
    }

    public static SilenceSegmentWorkerPayload payload(
            Double startSec,
            Double endSec,
            Double durationSec
    ) {
        return new SilenceSegmentWorkerPayload(startSec, endSec, durationSec);
    }

    public static SilenceSegment toEntity(VodJob vodJob, SilenceSegmentWorkerPayload payload) {
        return SilenceSegment.create(
                vodJob,
                payload.startSec(),
                payload.endSec(),
                payload.durationSec()
        );
    }

    public static List<SilenceSegment> toEntities(VodJob vodJob, List<SilenceSegmentWorkerPayload> payloads) {
        return payloads.stream()
                .map(payload -> toEntity(vodJob, payload))
                .toList();
    }
}
