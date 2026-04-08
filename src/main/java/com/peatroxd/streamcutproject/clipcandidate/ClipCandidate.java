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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clip_candidate")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "export_status", nullable = false, length = 20)
    private ExportStatus exportStatus;

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
        candidate.setExportStatus(ExportStatus.NOT_REQUESTED);
        return candidate;
    }
}
