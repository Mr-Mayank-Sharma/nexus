package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_carrier_label_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "carrier_code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxCarrierLabelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "carrier_code", nullable = false)
    private String carrierCode; // JITSU, SAPI, VHO, FEDEX, UPS...

    @Column(name = "adapter_name", nullable = false)
    private String adapterName; // JitsuCarrierAdapter, SapiCarrierAdapter...

    @Column(name = "label_format", nullable = false)
    private String labelFormat; // ZPL | PDF

    @Column(name = "required_fields", columnDefinition = "jsonb")
    private String requiredFields; // JSON array of required fields

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (labelFormat == null) labelFormat = "PDF";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}