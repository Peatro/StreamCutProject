package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.vodjob.VodJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clip_candidate")
public class ClipCandidate {

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

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "transcript_excerpt", nullable = false, columnDefinition = "TEXT")
    private String transcriptExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus;

    @Column(name = "moderator_note", columnDefinition = "TEXT")
    private String moderatorNote;

    @Column(name = "exported_clip_path", length = 512)
    private String exportedClipPath;

    protected ClipCandidate() {
    }

    public static ClipCandidate create(
            VodJob vodJob,
            Double startSec,
            Double endSec,
            Double score,
            String transcriptExcerpt
    ) {
        ClipCandidate candidate = new ClipCandidate();
        candidate.setVodJob(vodJob);
        candidate.setStartSec(startSec);
        candidate.setEndSec(endSec);
        candidate.setScore(score);
        candidate.setTranscriptExcerpt(transcriptExcerpt);
        candidate.setModerationStatus(ModerationStatus.PENDING);
        return candidate;
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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getTranscriptExcerpt() {
        return transcriptExcerpt;
    }

    public void setTranscriptExcerpt(String transcriptExcerpt) {
        this.transcriptExcerpt = transcriptExcerpt;
    }

    public ModerationStatus getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(ModerationStatus moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    public String getModeratorNote() {
        return moderatorNote;
    }

    public void setModeratorNote(String moderatorNote) {
        this.moderatorNote = moderatorNote;
    }

    public String getExportedClipPath() {
        return exportedClipPath;
    }

    public void setExportedClipPath(String exportedClipPath) {
        this.exportedClipPath = exportedClipPath;
    }
}
