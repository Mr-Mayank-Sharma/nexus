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

public interface AiPromptRepository extends JpaRepository<AiPrompt, UUID> {
    Page<AiPrompt> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<AiPrompt> findByTenantIdAndNameAndVersion(UUID tenantId, String name, Integer version);
    List<AiPrompt> findByTenantIdAndNameOrderByVersionDesc(UUID tenantId, String name);
    Page<AiPrompt> findByTenantIdAndModelId(UUID tenantId, UUID modelId, Pageable pageable);
}