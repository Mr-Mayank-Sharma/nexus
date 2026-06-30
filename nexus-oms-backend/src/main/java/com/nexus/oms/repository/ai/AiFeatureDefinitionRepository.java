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

public interface AiFeatureDefinitionRepository extends JpaRepository<AiFeatureDefinition, UUID> {
    Page<AiFeatureDefinition> findByTenantId(UUID tenantId, Pageable pageable);
    List<AiFeatureDefinition> findByTenantIdAndFeatureGroup(UUID tenantId, String featureGroup);
    Optional<AiFeatureDefinition> findByTenantIdAndName(UUID tenantId, String name);
    List<AiFeatureDefinition> findByTenantIdAndEntityType(UUID tenantId, String entityType);
    long countByTenantIdAndFeatureGroup(UUID tenantId, String featureGroup);
}