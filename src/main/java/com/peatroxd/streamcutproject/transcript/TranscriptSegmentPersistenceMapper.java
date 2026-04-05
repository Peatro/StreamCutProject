package com.peatroxd.streamcutproject.transcript;

import com.peatroxd.streamcutproject.vodjob.VodJob;

import java.util.List;

public final class TranscriptSegmentPersistenceMapper {

    private TranscriptSegmentPersistenceMapper() {
    }

    public static TranscriptSegmentWorkerPayload payload(
            Double startSec,
            Double endSec,
            String text,
            Integer wordCount
    ) {
        return new TranscriptSegmentWorkerPayload(startSec, endSec, text, wordCount);
    }

    public static TranscriptSegment toEntity(VodJob vodJob, TranscriptSegmentWorkerPayload payload) {
        return TranscriptSegment.create(
                vodJob,
                payload.startSec(),
                payload.endSec(),
                payload.text(),
                payload.wordCount()
        );
    }

    public static List<TranscriptSegment> toEntities(VodJob vodJob, List<TranscriptSegmentWorkerPayload> payloads) {
        return payloads.stream()
                .map(payload -> toEntity(vodJob, payload))
                .toList();
    }
}
