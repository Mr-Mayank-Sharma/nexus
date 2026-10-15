package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Marks an event id as handled by the Kafka consumer so a redelivery is skipped. Not tenant data: ids are random UUIDs. */
@Entity
@Table(name = "nx_processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NxProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    public NxProcessedEvent(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
    }
}
