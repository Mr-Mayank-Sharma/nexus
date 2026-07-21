package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<NxPromotion, UUID> {

    List<NxPromotion> findByTenantIdOrderByPriorityAsc(UUID tenantId);

    List<NxPromotion> findByTenantIdAndActiveTrueOrderByPriorityAsc(UUID tenantId);

    Optional<NxPromotion> findByCouponCode(String couponCode);

    @Query("SELECT p FROM NxPromotion p WHERE p.tenantId = :tenantId AND p.active = true " +
           "AND p.startDate <= :now AND p.endDate >= :now " +
           "ORDER BY p.priority ASC")
    List<NxPromotion> findActivePromotions(UUID tenantId, LocalDateTime now);

    @Query("SELECT p FROM NxPromotion p WHERE p.tenantId = :tenantId AND p.active = true " +
           "AND p.couponCode = :couponCode AND p.startDate <= :now AND p.endDate >= :now")
    Optional<NxPromotion> findActivePromotionByCoupon(UUID tenantId, String couponCode, LocalDateTime now);
}
