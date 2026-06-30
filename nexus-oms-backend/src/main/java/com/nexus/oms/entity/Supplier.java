package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotBlank
    @Column(name = "supplier_code", nullable = false)
    private String supplierCode;

    @NotBlank
    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "trading_name")
    private String tradingName;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "supplier_type")
    private String supplierType;

    private String status;

    @Column(name = "payment_terms")
    private String paymentTerms;

    private String currency;

    @PositiveOrZero
    @Column(name = "credit_limit")
    private BigDecimal creditLimit;

    private Integer rating;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;

    private String state;

    @Column(name = "zip_code")
    private String zipCode;

    private String country;

    private String phone;

    @Email
    private String email;

    private String website;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
