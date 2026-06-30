package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_warehouse_staff")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "user_id")
    private UUID userId;

    @NotBlank
    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @NotBlank
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String role;

    private String shift;

    @Column(columnDefinition = "jsonb")
    private String skills;

    @PositiveOrZero
    @Column(name = "productivity_score")
    private BigDecimal productivityScore;

    @Column(name = "items_picked_today")
    private Integer itemsPickedToday;

    @Column(name = "items_packed_today")
    private Integer itemsPackedToday;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "cert_expires_at")
    private LocalDateTime certExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (itemsPickedToday == null) itemsPickedToday = 0;
        if (itemsPackedToday == null) itemsPackedToday = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
