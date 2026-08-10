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

public interface AiModelRepository extends JpaRepository<AiModel, UUID> {
    Page<AiModel> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiModel> findByTenantIdAndCategory(UUID tenantId, String category, Pageable pageable);
    Page<AiModel> findByTenantIdAndModelType(UUID tenantId, String modelType, Pageable pageable);
    Page<AiModel> findByCategory(String category, Pageable pageable);
    Optional<AiModel> findByTenantIdAndName(UUID tenantId, String name);
    Page<AiModel> findByStatus(String status, Pageable pageable);
    Page<AiModel> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByTenantIdAndCategory(UUID tenantId, String category);
    long countByCategory(String category);
    long countByStatus(String status);
    @Query("SELECT m FROM AiModel m WHERE m.tenantId = :tenantId OR m.category = 'GLOBAL' ORDER BY m.createdAt DESC")
    Page<AiModel> findAllForTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
    @Query("SELECT m FROM AiModel m WHERE (m.tenantId = :tenantId OR m.category = 'GLOBAL') AND m.category = :category ORDER BY m.createdAt DESC")
    Page<AiModel> findAllForTenantAndCategory(@Param("tenantId") UUID tenantId, @Param("category") String category, Pageable pageable);
    @Query("SELECT m FROM AiModel m WHERE (m.tenantId = :tenantId OR m.category = 'GLOBAL') AND m.status = :status ORDER BY m.createdAt DESC")
    Page<AiModel> findAllForTenantAndStatus(@Param("tenantId") UUID tenantId, @Param("status") String status, Pageable pageable);
    @Query("SELECT m FROM AiModel m WHERE m.modelType = :type AND (m.tenantId = :tenantId OR m.category = 'GLOBAL') ORDER BY m.createdAt DESC")
    Page<AiModel> findAvailableForTenant(@Param("tenantId") UUID tenantId, @Param("type") String modelType, Pageable pageable);
}