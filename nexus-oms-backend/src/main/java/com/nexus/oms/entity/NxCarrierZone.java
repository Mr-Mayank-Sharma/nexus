package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_carrier_zones")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxCarrierZone {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "carrier_code", nullable = false)
    private String carrierCode;

    @Column(name = "zone_code", nullable = false)
    private String zoneCode;

    @Column(name = "zip_prefix", nullable = false)
    private String zipPrefix;

    private String country;

    @Column(name = "is_origin")
    private Boolean isOrigin;

    @Column(name = "is_destination")
    private Boolean isDestination;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isOrigin == null) isOrigin = false;
        if (isDestination == null) isDestination = true;
        if (country == null) country = "US";
    }
}
