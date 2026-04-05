package com.peatroxd.streamcutproject.analysiswindow;

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
@Table(name = "analysis_window")
public class AnalysisWindow {

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

    @Column(name = "speech_density", nullable = false)
    private Double speechDensity;

    @Column(name = "silence_ratio", nullable = false)
    private Double silenceRatio;

    @Column(name = "emotion_hits", nullable = false)
    private Integer emotionHits;

    @Column(name = "continuity_score", nullable = false)
    private Double continuityScore;

    @Column(name = "total_score", nullable = false)
    private Double totalScore;

    protected AnalysisWindow() {
    }

    public static AnalysisWindow create(
            VodJob vodJob,
            Double startSec,
            Double endSec,
            Double speechDensity,
            Double silenceRatio,
            Integer emotionHits,
            Double continuityScore,
            Double totalScore
    ) {
        AnalysisWindow window = new AnalysisWindow();
        window.setVodJob(vodJob);
        window.setStartSec(startSec);
        window.setEndSec(endSec);
        window.setSpeechDensity(speechDensity);
        window.setSilenceRatio(silenceRatio);
        window.setEmotionHits(emotionHits);
        window.setContinuityScore(continuityScore);
        window.setTotalScore(totalScore);
        return window;
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

    public Double getSpeechDensity() {
        return speechDensity;
    }

    public void setSpeechDensity(Double speechDensity) {
        this.speechDensity = speechDensity;
    }

    public Double getSilenceRatio() {
        return silenceRatio;
    }

    public void setSilenceRatio(Double silenceRatio) {
        this.silenceRatio = silenceRatio;
    }

    public Integer getEmotionHits() {
        return emotionHits;
    }

    public void setEmotionHits(Integer emotionHits) {
        this.emotionHits = emotionHits;
    }

    public Double getContinuityScore() {
        return continuityScore;
    }

    public void setContinuityScore(Double continuityScore) {
        this.continuityScore = continuityScore;
    }

    public Double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Double totalScore) {
        this.totalScore = totalScore;
    }
}
