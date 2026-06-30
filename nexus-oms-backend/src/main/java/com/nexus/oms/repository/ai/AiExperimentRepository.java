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

public interface AiExperimentRepository extends JpaRepository<AiExperiment, UUID> {
    Page<AiExperiment> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiExperiment> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    List<AiExperiment> findByModelIdAndStatus(UUID modelId, String status);
    Page<AiExperiment> findByModelId(UUID modelId, Pageable pageable);
}