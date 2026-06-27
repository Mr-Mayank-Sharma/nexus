package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_tracking_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "event_type")
    private String eventType;

    private String location;

    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;
}
