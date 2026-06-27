package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_edi_partners")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxEdiPartner {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "partner_code", nullable = false)
    private String partnerCode;

    @Column(name = "partner_name", nullable = false)
    private String partnerName;

    private String qualifier;

    @Column(name = "interchange_id")
    private String interchangeId;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "supported_docs", columnDefinition = "jsonb")
    private String supportedDocs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (qualifier == null) qualifier = "ZZ";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
