package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.ClipCandidate;
import com.peatroxd.streamcutproject.clipcandidate.ExportStatus;
import com.peatroxd.streamcutproject.clipcandidate.ModerationStatus;
import com.peatroxd.streamcutproject.transcript.TranscriptSegment;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentRepository;
import com.peatroxd.streamcutproject.transcript.TranscriptWordPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayload;
import com.peatroxd.streamcutproject.workerdispatch.WorkerDispatchPayloadFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerDispatchPayloadFactoryTest {

    @Mock
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @InjectMocks
    private WorkerDispatchPayloadFactory factory;

    @Test
    void buildsAnalyzePayloadFromJobContractFields() {
        VodJob job = new VodJob();
        job.setId(42L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");
        job.setStorageVideoPath("/var/lib/streamcut/jobs/42/source/video.mp4");
        job.setSourceVideoReference("s3://streamcut-artifacts/sources/jobs/42/source-video.mp4");
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(Instant.parse("2026-04-05T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-04-05T10:00:00Z"));

        WorkerDispatchPayload payload = factory.fromAnalyzeJob(job, 100L);

        assertThat(payload.executionId()).isEqualTo(100L);
        assertThat(payload.jobId()).isEqualTo(42L);
        assertThat(payload.taskType()).isEqualTo("ANALYZE");
        assertThat(payload.videoPath()).isEqualTo("/var/lib/streamcut/jobs/42/source/video.mp4");
        assertThat(payload.videoReference()).isEqualTo("s3://streamcut-artifacts/sources/jobs/42/source-video.mp4");
        assertThat(payload.videoDownloadUrl()).isEqualTo("/api/internal/worker/jobs/42/source/file");
        assertThat(payload.sourceType()).isEqualTo("URL");
        assertThat(payload.sourceUrl()).isEqualTo("https://example.com/video");
        assertThat(payload.clipWords()).isEmpty();
    }

    @Test
    void rejectsJobsWithoutDurableSourceVideoReference() {
        VodJob job = new VodJob();
        job.setId(42L);
        job.setSourceType("FILE");

        assertThatThrownBy(() -> factory.fromAnalyzeJob(job, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no durable source video reference");
    }

    @Test
    void buildsDownloadPayloadWithoutStorageVideoPath() {
        VodJob job = new VodJob();
        job.setId(42L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");

        WorkerDispatchPayload payload = factory.fromDownloadJob(job, 101L);

        assertThat(payload.executionId()).isEqualTo(101L);
        assertThat(payload.jobId()).isEqualTo(42L);
        assertThat(payload.taskType()).isEqualTo("DOWNLOAD");
        assertThat(payload.videoPath()).isNull();
        assertThat(payload.videoReference()).isNull();
        assertThat(payload.videoDownloadUrl()).isNull();
        assertThat(payload.sourceUrl()).isEqualTo("https://example.com/video");
        assertThat(payload.clipWords()).isEmpty();
    }

    @Test
    void exportPayloadIncludesWordsWithinClipWindow() {
        VodJob job = new VodJob();
        job.setId(10L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");
        job.setStorageVideoPath("/var/lib/streamcut/jobs/10/source/video.mp4");

        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 15.0, 0.9, "hello world");
        candidate.setId(3L);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/10/exports/candidate-3.mp4");
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);

        // Segment with words spanning the clip window and beyond
        String wordsJson = """
                [
                  {"word":"before","startSec":2.0,"endSec":3.0},
                  {"word":"hello","startSec":5.0,"endSec":7.0},
                  {"word":"world","startSec":8.0,"endSec":10.0},
                  {"word":"after","startSec":16.0,"endSec":18.0}
                ]
                """;
        TranscriptSegment segment = TranscriptSegment.create(job, 0.0, 20.0, "before hello world after", 4, wordsJson);

        when(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(10L))
                .thenReturn(List.of(segment));

        WorkerDispatchPayload payload = factory.fromExportCandidate(candidate, 200L);

        assertThat(payload.taskType()).isEqualTo("EXPORT");
        assertThat(payload.candidateId()).isEqualTo(3L);
        assertThat(payload.clipStartSec()).isEqualTo(5.0);
        assertThat(payload.clipEndSec()).isEqualTo(15.0);
        assertThat(payload.clipWords()).hasSize(2);
        assertThat(payload.clipWords()).containsExactly(
                new TranscriptWordPayload("hello", 5.0, 7.0),
                new TranscriptWordPayload("world", 8.0, 10.0)
        );
    }

    @Test
    void exportPayloadExcludesOutOfWindowWords() {
        VodJob job = new VodJob();
        job.setId(10L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");
        job.setStorageVideoPath("/var/lib/streamcut/jobs/10/source/video.mp4");

        ClipCandidate candidate = ClipCandidate.create(job, 10.0, 20.0, 0.85, "excerpt");
        candidate.setId(5L);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/10/exports/candidate-5.mp4");
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);

        // All words fall outside the clip window
        String wordsJson = """
                [
                  {"word":"early","startSec":1.0,"endSec":3.0},
                  {"word":"late","startSec":25.0,"endSec":27.0}
                ]
                """;
        TranscriptSegment segment = TranscriptSegment.create(job, 0.0, 30.0, "early late", 2, wordsJson);

        when(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(10L))
                .thenReturn(List.of(segment));

        WorkerDispatchPayload payload = factory.fromExportCandidate(candidate, 201L);

        assertThat(payload.clipWords()).isEmpty();
    }

    @Test
    void exportPayloadReturnsEmptyClipWordsForWordlessJob() {
        VodJob job = new VodJob();
        job.setId(10L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");
        job.setStorageVideoPath("/var/lib/streamcut/jobs/10/source/video.mp4");

        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 15.0, 0.9, "some excerpt");
        candidate.setId(4L);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/10/exports/candidate-4.mp4");
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);

        // Segment without words (null wordsJson -- legacy / word-less transcript)
        TranscriptSegment segment = TranscriptSegment.create(job, 0.0, 20.0, "some transcript text", 3);

        when(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(10L))
                .thenReturn(List.of(segment));

        WorkerDispatchPayload payload = factory.fromExportCandidate(candidate, 202L);

        assertThat(payload.clipWords()).isEmpty();
        assertThat(payload.taskType()).isEqualTo("EXPORT");
    }

    @Test
    void exportPayloadReturnsEmptyClipWordsWhenNoSegmentsExist() {
        VodJob job = new VodJob();
        job.setId(10L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");
        job.setStorageVideoPath("/var/lib/streamcut/jobs/10/source/video.mp4");

        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 15.0, 0.9, "excerpt");
        candidate.setId(6L);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/10/exports/candidate-6.mp4");
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);

        when(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(10L))
                .thenReturn(List.of());

        WorkerDispatchPayload payload = factory.fromExportCandidate(candidate, 203L);

        assertThat(payload.clipWords()).isEmpty();
    }

    @Test
    void exportPayloadIncludesWordsFromMultipleSegments() {
        VodJob job = new VodJob();
        job.setId(10L);
        job.setSourceType("URL");
        job.setSourceUrl("https://example.com/video");
        job.setStorageVideoPath("/var/lib/streamcut/jobs/10/source/video.mp4");

        ClipCandidate candidate = ClipCandidate.create(job, 5.0, 20.0, 0.88, "multi segment");
        candidate.setId(8L);
        candidate.setExportedClipPath("/var/lib/streamcut/jobs/10/exports/candidate-8.mp4");
        candidate.setExportStatus(ExportStatus.IN_PROGRESS);

        String wordsJson1 = """
                [{"word":"alpha","startSec":5.0,"endSec":7.0}]
                """;
        String wordsJson2 = """
                [{"word":"beta","startSec":12.0,"endSec":14.0}]
                """;
        TranscriptSegment seg1 = TranscriptSegment.create(job, 0.0, 10.0, "alpha", 1, wordsJson1);
        TranscriptSegment seg2 = TranscriptSegment.create(job, 10.0, 20.0, "beta", 1, wordsJson2);

        when(transcriptSegmentRepository.findAllByJobIdOrderByStartSecAscIdAsc(10L))
                .thenReturn(List.of(seg1, seg2));

        WorkerDispatchPayload payload = factory.fromExportCandidate(candidate, 204L);

        assertThat(payload.clipWords()).containsExactly(
                new TranscriptWordPayload("alpha", 5.0, 7.0),
                new TranscriptWordPayload("beta", 12.0, 14.0)
        );
    }
}
