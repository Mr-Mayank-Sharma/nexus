package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_supplier_contracts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @NotBlank
    @Column(name = "contract_number", nullable = false)
    private String contractNumber;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private String terms;

    @Column(name = "pricing_terms", columnDefinition = "jsonb")
    private String pricingTerms;

    @PositiveOrZero
    @Column(name = "discount_percent")
    private BigDecimal discountPercent;

    private String status;

    @Column(name = "auto_renew")
    private Boolean autoRenew;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
