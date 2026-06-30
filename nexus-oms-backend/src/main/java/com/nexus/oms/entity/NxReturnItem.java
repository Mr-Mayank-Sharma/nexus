package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_return_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxReturnItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "return_id", nullable = false)
    private UUID returnId;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @NotBlank
    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "return_reason_detail", columnDefinition = "TEXT")
    private String returnReasonDetail;

    @Column(length = 30)
    private String condition;

    @Column(name = "condition_notes", columnDefinition = "TEXT")
    private String conditionNotes;

    @Size(max = 10)
    @Column(length = 10)
    private String grade;

    @Column(length = 50)
    private String disposition;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(length = 30)
    private String status;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "inspected_by")
    private UUID inspectedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (condition == null) condition = "UNKNOWN";
        if (disposition == null) disposition = "PENDING";
        if (quantity == null) quantity = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
