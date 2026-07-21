package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionUsageRepository extends JpaRepository<NxPromotionUsage, UUID> {

    List<NxPromotionUsage> findByPromotionIdOrderByUsedAtDesc(UUID promotionId);

    List<NxPromotionUsage> findByCustomerIdOrderByUsedAtDesc(UUID customerId);

    long countByPromotionId(UUID promotionId);

    @Query("SELECT COUNT(u) FROM NxPromotionUsage u WHERE u.promotionId = :promotionId AND u.customerId = :customerId")
    long countByPromotionIdAndCustomerId(UUID promotionId, UUID customerId);

    @Query("SELECT COALESCE(SUM(u.discountAmount), 0) FROM NxPromotionUsage u WHERE u.promotionId = :promotionId")
    java.math.BigDecimal sumDiscountByPromotionId(UUID promotionId);
}
