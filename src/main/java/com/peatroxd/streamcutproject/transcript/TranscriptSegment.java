package com.peatroxd.streamcutproject.transcript;

import com.peatroxd.streamcutproject.vodjob.VodJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transcript_segment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranscriptSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private VodJob vodJob;

    @Column(name = "start_sec", nullable = false)
    private Double startSec;

    @Column(name = "end_sec", nullable = false)
    private Double endSec;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "word_count", nullable = false)
    private Integer wordCount;

    @Column(name = "words_json", columnDefinition = "TEXT")
    private String wordsJson;

    public static TranscriptSegment create(
            VodJob vodJob,
            Double startSec,
            Double endSec,
            String text,
            Integer wordCount
    ) {
        TranscriptSegment segment = new TranscriptSegment();
        segment.setVodJob(vodJob);
        segment.setStartSec(startSec);
        segment.setEndSec(endSec);
        segment.setText(text);
        segment.setWordCount(wordCount);
        return segment;
    }

    public static TranscriptSegment create(
            VodJob vodJob,
            Double startSec,
            Double endSec,
            String text,
            Integer wordCount,
            String wordsJson
    ) {
        TranscriptSegment segment = create(vodJob, startSec, endSec, text, wordCount);
        segment.setWordsJson(wordsJson);
        return segment;
    }
}
