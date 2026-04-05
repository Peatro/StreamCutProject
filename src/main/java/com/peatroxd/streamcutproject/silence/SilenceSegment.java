package com.peatroxd.streamcutproject.silence;

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
@Table(name = "silence_segment")
public class SilenceSegment {

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

    @Column(name = "duration_sec", nullable = false)
    private Double durationSec;

    protected SilenceSegment() {
    }

    public static SilenceSegment create(VodJob vodJob, Double startSec, Double endSec, Double durationSec) {
        SilenceSegment segment = new SilenceSegment();
        segment.setVodJob(vodJob);
        segment.setStartSec(startSec);
        segment.setEndSec(endSec);
        segment.setDurationSec(durationSec);
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

    public Double getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Double durationSec) {
        this.durationSec = durationSec;
    }
}
