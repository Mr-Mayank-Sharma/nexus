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

public interface AiCostLogRepository extends JpaRepository<AiCostLog, UUID> {
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM AiCostLog c WHERE c.tenantId = :tenantId AND c.costType = :costType AND c.recordedAt >= :since")
    java.math.BigDecimal sumByTenantAndTypeSince(@Param("tenantId") UUID tenantId, @Param("costType") String costType, @Param("since") LocalDateTime since);
    @Query("SELECT c.costType, SUM(c.amount) FROM AiCostLog c WHERE c.tenantId = :tenantId AND c.recordedAt >= :since GROUP BY c.costType")
    List<Object[]> breakdownByTenantSince(@Param("tenantId") UUID tenantId, @Param("since") LocalDateTime since);
    Page<AiCostLog> findByTenantId(UUID tenantId, Pageable pageable);
}