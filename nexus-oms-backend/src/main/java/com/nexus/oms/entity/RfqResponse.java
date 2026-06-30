package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_rfq_responses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfqResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "rfq_id", nullable = false)
    private UUID rfqId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @PositiveOrZero
    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    private String currency;

    @Positive
    @Column(name = "delivery_days")
    private Integer deliveryDays;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    private String notes;

    private String status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
