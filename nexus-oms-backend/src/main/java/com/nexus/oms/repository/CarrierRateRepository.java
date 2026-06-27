package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCarrierRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CarrierRateRepository extends JpaRepository<NxCarrierRate, UUID> {
    List<NxCarrierRate> findByTenantIdAndIsActiveTrue(UUID tenantId);

    @Query("SELECT r FROM NxCarrierRate r WHERE r.tenantId = :tenantId AND r.isActive = true " +
           "AND r.weightMinKg <= :weight AND r.weightMaxKg >= :weight " +
           "ORDER BY r.baseRate ASC")
    List<NxCarrierRate> findEligibleRates(@Param("tenantId") UUID tenantId, @Param("weight") BigDecimal weight);

    @Query("SELECT r FROM NxCarrierRate r WHERE r.tenantId = :tenantId AND r.isActive = true " +
           "AND r.weightMinKg <= :weight AND r.weightMaxKg >= :weight " +
           "AND r.serviceLevel IN :serviceLevels " +
           "ORDER BY r.baseRate ASC")
    List<NxCarrierRate> findEligibleRatesByService(@Param("tenantId") UUID tenantId,
                                                     @Param("weight") BigDecimal weight,
                                                     @Param("serviceLevels") List<String> serviceLevels);

    List<NxCarrierRate> findByTenantIdAndCarrierCode(UUID tenantId, String carrierCode);

    List<NxCarrierRate> findByTenantIdAndServiceLevel(UUID tenantId, String serviceLevel);
}
