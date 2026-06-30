package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_packages")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxPackage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "picklist_id")
    private UUID picklistId;

    @Column(name = "package_type")
    private String packageType;

    @Column(name = "box_name")
    private String boxName;

    @Column(name = "weight_lbs")
    private Double weightLbs;

    @Column(name = "width_in")
    private Double widthIn;

    @Column(name = "height_in")
    private Double heightIn;

    @Column(name = "depth_in")
    private Double depthIn;

    @Column(columnDefinition = "jsonb")
    private String items;

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "carrier_id")
    private UUID carrierId;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(name = "service_level")
    private String serviceLevel;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "label_format")
    private String labelFormat;

    @Column(name = "shipping_cost")
    private Double shippingCost;

    private String status;

    private String notes;

    @Column(name = "packed_by")
    private String packedBy;

    @Column(name = "packed_at")
    private LocalDateTime packedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING_PACK";
        if (packageType == null) packageType = "BOX";
        if (itemCount == null) itemCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
