package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "nx_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(columnDefinition = "jsonb")
    private String address;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "capacity_daily")
    private Integer capacityDaily;

    @Column(name = "cut_off_time")
    private LocalTime cutOffTime;

    @Column(name = "carrier_config", columnDefinition = "jsonb")
    private String carrierConfig;

    @PrePersist
    protected void onCreate() {
        if (isActive == null) isActive = true;
    }
}
