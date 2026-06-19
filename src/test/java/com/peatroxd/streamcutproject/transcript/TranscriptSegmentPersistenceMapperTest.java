package com.peatroxd.streamcutproject.transcript;

import com.peatroxd.streamcutproject.vodjob.JobStatus;
import com.peatroxd.streamcutproject.vodjob.VodJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptSegmentPersistenceMapperTest {

    @Test
    void mapsWorkerPayloadToEntity() {
        VodJob job = buildJob(42L);
        TranscriptSegmentWorkerPayload payload = TranscriptSegmentPersistenceMapper.payload(
                12.5,
                18.25,
                "hello world",
                2
        );

        TranscriptSegment segment = TranscriptSegmentPersistenceMapper.toEntity(job, payload);

        assertThat(segment.getVodJob()).isSameAs(job);
        assertThat(segment.getStartSec()).isEqualTo(12.5);
        assertThat(segment.getEndSec()).isEqualTo(18.25);
        assertThat(segment.getText()).isEqualTo("hello world");
        assertThat(segment.getWordCount()).isEqualTo(2);
        assertThat(segment.getWordsJson()).isNull();
    }

    @Test
    void mapsWorkerPayloadWithWordsToEntity() {
        VodJob job = buildJob(42L);
        List<TranscriptWordPayload> words = List.of(
                new TranscriptWordPayload("hello", 12.5, 13.0),
                new TranscriptWordPayload("world", 13.1, 14.0)
        );
        TranscriptSegmentWorkerPayload payload = TranscriptSegmentPersistenceMapper.payload(
                12.5,
                18.25,
                "hello world",
                2,
                words
        );

        TranscriptSegment segment = TranscriptSegmentPersistenceMapper.toEntity(job, payload);

        assertThat(segment.getVodJob()).isSameAs(job);
        assertThat(segment.getStartSec()).isEqualTo(12.5);
        assertThat(segment.getEndSec()).isEqualTo(18.25);
        assertThat(segment.getText()).isEqualTo("hello world");
        assertThat(segment.getWordCount()).isEqualTo(2);
        assertThat(segment.getWordsJson()).isNotNull();

        List<TranscriptWordPayload> parsed = TranscriptSegmentPersistenceMapper.wordsFromJson(segment.getWordsJson());
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).word()).isEqualTo("hello");
        assertThat(parsed.get(0).startSec()).isEqualTo(12.5);
        assertThat(parsed.get(0).endSec()).isEqualTo(13.0);
        assertThat(parsed.get(1).word()).isEqualTo("world");
    }

    @Test
    void wordsFromJsonReturnsEmptyForNull() {
        assertThat(TranscriptSegmentPersistenceMapper.wordsFromJson(null)).isEmpty();
    }

    @Test
    void wordsFromJsonReturnsEmptyForBlank() {
        assertThat(TranscriptSegmentPersistenceMapper.wordsFromJson("")).isEmpty();
    }

    @Test
    void mapsMultipleWorkerPayloadsInOrder() {
        VodJob job = buildJob(42L);
        List<TranscriptSegment> segments = TranscriptSegmentPersistenceMapper.toEntities(
                job,
                List.of(
                        TranscriptSegmentPersistenceMapper.payload(20.0, 24.0, "second", 1),
                        TranscriptSegmentPersistenceMapper.payload(10.0, 15.0, "first", 1)
                )
        );

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getText()).isEqualTo("second");
        assertThat(segments.get(1).getText()).isEqualTo("first");
    }

    private static VodJob buildJob(Long id) {
        VodJob job = newVodJob();
        job.setId(id);
        job.setSourceType("URL");
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        return job;
    }

    private static VodJob newVodJob() {
        try {
            Constructor<VodJob> constructor = VodJob.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create VodJob test fixture", ex);
        }
    }
}
