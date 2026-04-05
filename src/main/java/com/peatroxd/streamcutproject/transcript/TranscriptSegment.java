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

@Entity
@Table(name = "transcript_segment")
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

    protected TranscriptSegment() {
    }

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VodJob getVodJob() {
        return vodJob;
    }

    public void setVodJob(VodJob vodJob) {
        this.vodJob = vodJob;
    }

    public Double getStartSec() {
        return startSec;
    }

    public void setStartSec(Double startSec) {
        this.startSec = startSec;
    }

    public Double getEndSec() {
        return endSec;
    }

    public void setEndSec(Double endSec) {
        this.endSec = endSec;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }
}
