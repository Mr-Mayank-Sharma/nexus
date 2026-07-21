package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_rejection_reasons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxRejectionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "description")
    private String description;

    @Column(name = "category", nullable = false)
    private String category; // QUALITY, DAMAGED, WRONG_ITEM, CUSTOMER, INVENTORY, OTHER

    @Column(name = "inventory_impact", nullable = false)
    private String inventoryImpact; // RESTOCK, DAMAGE_WRITE_OFF, RETURN_TO_VENDOR, QUARANTINE

    @Column(name = "requires_photo", nullable = false)
    private Boolean requiresPhoto;

    @Column(name = "requires_notes", nullable = false)
    private Boolean requiresNotes;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) active = true;
        if (requiresPhoto == null) requiresPhoto = false;
        if (requiresNotes == null) requiresNotes = false;
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
