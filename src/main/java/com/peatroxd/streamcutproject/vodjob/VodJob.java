package com.peatroxd.streamcutproject.vodjob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "vod_job")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VodJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private JobStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_sec")
    private Long durationSec;

    @Column(name = "language", length = 64)
    private String language;

    @Column(name = "storage_video_path", length = 512)
    private String storageVideoPath;

    @Column(name = "storage_audio_path", length = 512)
    private String storageAudioPath;

    @Column(name = "processing_version", nullable = false)
    private Long processingVersion = 1L;

    @Column(name = "current_worker_id", length = 128)
    private String currentWorkerId;

    @Column(name = "last_worker_heartbeat_at")
    private Instant lastWorkerHeartbeatAt;

    @Column(name = "progress_percent")
    private Integer progressPercent;

    @Column(name = "progress_message", length = 255)
    private String progressMessage;

}
