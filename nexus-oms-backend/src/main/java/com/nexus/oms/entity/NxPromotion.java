package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_promotions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "promotion_type", nullable = false)
    private String promotionType; // PERCENTAGE, FIXED_AMOUNT, BOGO, FREE_SHIPPING, BUY_X_GET_Y

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "min_quantity")
    private Integer minQuantity;

    @Column(name = "max_uses_total")
    private Integer maxUsesTotal;

    @Column(name = "max_uses_per_customer")
    private Integer maxUsesPerCustomer;

    @Column(name = "current_uses", nullable = false)
    private Integer currentUses;

    @Column(name = "coupon_code", unique = true)
    private String couponCode;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "applicable_channels")
    private String applicableChannels; // comma-separated: ONLINE,IN_STORE,MOBILE,ALL

    @Column(name = "applicable_product_ids")
    private String applicableProductIds; // comma-separated product IDs, null = all products

    @Column(name = "applicable_category_ids")
    private String applicableCategoryIds; // comma-separated category IDs

    @Column(name = "stackable", nullable = false)
    private Boolean stackable;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currentUses == null) currentUses = 0;
        if (stackable == null) stackable = false;
        if (active == null) active = true;
        if (priority == null) priority = 10;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
