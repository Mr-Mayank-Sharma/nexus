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

public interface AiTrainingJobRepository extends JpaRepository<AiTrainingJob, UUID> {
    Page<AiTrainingJob> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiTrainingJob> findByModelId(UUID modelId, Pageable pageable);
    List<AiTrainingJob> findByModelIdAndStatus(UUID modelId, String status);
    Page<AiTrainingJob> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByModelIdAndStatus(UUID modelId, String status);
    @Query("SELECT COALESCE(AVG(j.accuracy), 0) FROM AiTrainingJob j WHERE j.modelId = :modelId AND j.status = 'COMPLETED'")
    java.math.BigDecimal avgAccuracyByModel(@Param("modelId") UUID modelId);
}