package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_slotting_audits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSlottingAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @NotBlank
    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "from_bin_id")
    private UUID fromBinId;

    @Column(name = "from_bin_code")
    private String fromBinCode;

    @Column(name = "to_bin_id")
    private UUID toBinId;

    @Column(name = "to_bin_code")
    private String toBinCode;

    @Column(name = "from_zone_id")
    private UUID fromZoneId;

    @Column(name = "to_zone_id")
    private UUID toZoneId;

    private String reason;

    private String action;

    @Column(name = "moved_quantity")
    private Integer movedQuantity;

    @Column(name = "performed_by")
    private String performedBy;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
