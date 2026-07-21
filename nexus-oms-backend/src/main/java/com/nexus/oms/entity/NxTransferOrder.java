package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_transfer_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxTransferOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Column(name = "transfer_number", nullable = false, unique = true)
    private String transferNumber;

    @NotBlank
    @Column(name = "transfer_type", nullable = false)
    private String transferType; // WAREHOUSE_TO_STORE, STORE_TO_STORE, STORE_TO_WAREHOUSE

    @Column(name = "source_node_id", nullable = false)
    private UUID sourceNodeId;

    @Column(name = "destination_node_id", nullable = false)
    private UUID destinationNodeId;

    @NotBlank
    @Column(nullable = false)
    private String status; // DRAFT, PENDING_APPROVAL, APPROVED, IN_TRANSIT, RECEIVED, CANCELLED

    @Column(nullable = false)
    private String priority; // LOW, NORMAL, HIGH, URGENT

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "expected_arrival")
    private LocalDateTime expectedArrival;

    @Column(name = "actual_arrival")
    private LocalDateTime actualArrival;

    private String notes;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "DRAFT";
        if (priority == null) priority = "NORMAL";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
