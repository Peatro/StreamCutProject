package com.peatroxd.streamcutproject.vodjob.event;

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

import java.time.Instant;

@Entity
@Table(name = "job_event")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private VodJob vodJob;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static JobEvent create(VodJob vodJob, String eventType, String message, Instant createdAt) {
        JobEvent event = new JobEvent();
        event.setVodJob(vodJob);
        event.setEventType(eventType);
        event.setMessage(message);
        event.setCreatedAt(createdAt);
        return event;
    }
}
