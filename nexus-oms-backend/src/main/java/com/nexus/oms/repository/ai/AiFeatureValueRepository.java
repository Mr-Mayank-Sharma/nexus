package com.nexus.oms.repository.ai;

import com.nexus.oms.entity.ai.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiFeatureValueRepository extends JpaRepository<AiFeatureValue, UUID> {
    List<AiFeatureValue> findByTenantIdAndFeatureIdAndEntityIdAndAsOfDate(UUID tenantId, UUID featureId, String entityId, LocalDate asOfDate);
    List<AiFeatureValue> findByTenantIdAndFeatureIdAndAsOfDate(UUID tenantId, UUID featureId, LocalDate asOfDate);
    @Query("SELECT f FROM AiFeatureValue f WHERE f.tenantId = :tenantId AND f.entityId = :entityId AND f.asOfDate = :date")
    List<AiFeatureValue> findAllForEntity(@Param("tenantId") UUID tenantId, @Param("entityId") String entityId, @Param("date") LocalDate date);
    void deleteByAsOfDateBefore(LocalDate date);
}