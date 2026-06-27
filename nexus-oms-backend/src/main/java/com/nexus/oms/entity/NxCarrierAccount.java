package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "nx_carrier_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxCarrierAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "carrier_id", nullable = false)
    private String carrierId;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "api_secret_encrypted", columnDefinition = "TEXT")
    private String apiSecretEncrypted;

    @Column(name = "negotiated_discount")
    private BigDecimal negotiatedDiscount;

    @Column(name = "contract_effective")
    private LocalDate contractEffective;

    @Column(name = "contract_expiry")
    private LocalDate contractExpiry;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "node_id")
    private UUID nodeId;

    @PrePersist
    protected void onCreate() {
        if (isActive == null) isActive = true;
    }
}
