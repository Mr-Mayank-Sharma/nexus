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

public interface AiInferenceLogRepository extends JpaRepository<AiInferenceLog, UUID> {
    Page<AiInferenceLog> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiInferenceLog> findByTenantIdAndModelId(UUID tenantId, UUID modelId, Pageable pageable);
    long countByTenantIdAndModelIdAndStatus(UUID tenantId, UUID modelId, String status);
    long countByTenantIdAndFallbackUsed(UUID tenantId, boolean fallbackUsed);
    long countByTenantIdAndRuleEngineUsed(UUID tenantId, boolean ruleEngineUsed);
    @Query("SELECT COALESCE(AVG(l.latencyMs), 0) FROM AiInferenceLog l WHERE l.modelId = :modelId AND l.createdAt >= :since")
    java.math.BigDecimal avgLatencyByModelSince(@Param("modelId") UUID modelId, @Param("since") LocalDateTime since);
    @Query("SELECT COALESCE(SUM(l.cost), 0) FROM AiInferenceLog l WHERE l.tenantId = :tenantId AND l.createdAt >= :since")
    java.math.BigDecimal totalCostByTenantSince(@Param("tenantId") UUID tenantId, @Param("since") LocalDateTime since);
    long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime after);
    long countByModelIdAndStatusAndCreatedAtAfter(UUID modelId, String status, LocalDateTime after);
    void deleteByCreatedAtBefore(LocalDateTime date);
}