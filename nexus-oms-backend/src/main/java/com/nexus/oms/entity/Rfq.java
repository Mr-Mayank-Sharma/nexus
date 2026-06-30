package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_rfqs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rfq {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotBlank
    @Column(name = "rfq_number", nullable = false)
    private String rfqNumber;

    @NotBlank
    @Column(nullable = false)
    private String title;

    private String description;

    private String status;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "supplier_ids", columnDefinition = "jsonb")
    private String supplierIds;

    private String terms;

    private String currency;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
