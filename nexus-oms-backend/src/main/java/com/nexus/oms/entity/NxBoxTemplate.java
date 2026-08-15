package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_box_templates")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxBoxTemplate {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "width_in", nullable = false)
    private Double widthIn;

    @Column(name = "height_in", nullable = false)
    private Double heightIn;

    @Column(name = "depth_in", nullable = false)
    private Double depthIn;

    @Column(name = "volume_capacity_in3", nullable = false)
    private Double volumeCapacityIn3;

    @Column(name = "max_weight_lbs", nullable = false)
    private Double maxWeightLbs;

    @Column(name = "max_item_count", nullable = false)
    private Integer maxItemCount;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (volumeCapacityIn3 == null && widthIn != null && heightIn != null && depthIn != null) {
            volumeCapacityIn3 = widthIn * heightIn * depthIn;
        }
        if (isActive == null) isActive = true;
        if (maxItemCount == null) maxItemCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
