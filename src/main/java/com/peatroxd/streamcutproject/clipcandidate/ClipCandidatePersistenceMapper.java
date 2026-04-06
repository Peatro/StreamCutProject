package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.vodjob.VodJob;

import java.util.List;

public final class ClipCandidatePersistenceMapper {

    private ClipCandidatePersistenceMapper() {
    }

    public static ClipCandidate toEntity(VodJob vodJob, ClipCandidateWorkerPayload payload) {
        return ClipCandidate.create(
                vodJob,
                payload.startSec(),
                payload.endSec(),
                payload.score(),
                payload.transcriptExcerpt()
        );
    }

    public static List<ClipCandidate> toEntities(VodJob vodJob, List<ClipCandidateWorkerPayload> payloads) {
        return payloads.stream()
                .map(payload -> toEntity(vodJob, payload))
                .toList();
    }
}
