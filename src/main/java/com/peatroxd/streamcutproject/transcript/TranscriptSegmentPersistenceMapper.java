package com.peatroxd.streamcutproject.transcript;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peatroxd.streamcutproject.vodjob.VodJob;

import java.util.List;

public final class TranscriptSegmentPersistenceMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TranscriptSegmentPersistenceMapper() {
    }

    public static TranscriptSegmentWorkerPayload payload(
            Double startSec,
            Double endSec,
            String text,
            Integer wordCount
    ) {
        return new TranscriptSegmentWorkerPayload(startSec, endSec, text, wordCount, null);
    }

    public static TranscriptSegmentWorkerPayload payload(
            Double startSec,
            Double endSec,
            String text,
            Integer wordCount,
            List<TranscriptWordPayload> words
    ) {
        return new TranscriptSegmentWorkerPayload(startSec, endSec, text, wordCount, words);
    }

    public static TranscriptSegment toEntity(VodJob vodJob, TranscriptSegmentWorkerPayload payload) {
        String wordsJson = wordsToJson(payload.words());
        return TranscriptSegment.create(
                vodJob,
                payload.startSec(),
                payload.endSec(),
                payload.text(),
                payload.wordCount(),
                wordsJson
        );
    }

    public static List<TranscriptSegment> toEntities(VodJob vodJob, List<TranscriptSegmentWorkerPayload> payloads) {
        return payloads.stream()
                .map(payload -> toEntity(vodJob, payload))
                .toList();
    }

    public static List<TranscriptWordPayload> wordsFromJson(String wordsJson) {
        if (wordsJson == null || wordsJson.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(wordsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    static String wordsToJson(List<TranscriptWordPayload> words) {
        if (words == null || words.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(words);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
