package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "nx_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotBlank
    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @PositiveOrZero
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @NotNull
    @PositiveOrZero
    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "allocated_node_id")
    private UUID allocatedNodeId;

    @Column(name = "allocated_qty")
    private Integer allocatedQty;
}
