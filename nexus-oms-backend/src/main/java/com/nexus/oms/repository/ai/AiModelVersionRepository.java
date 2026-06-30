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

public interface AiModelVersionRepository extends JpaRepository<AiModelVersion, UUID> {
    List<AiModelVersion> findByModelIdOrderByCreatedAtDesc(UUID modelId);
    Page<AiModelVersion> findByModelId(UUID modelId, Pageable pageable);
    Optional<AiModelVersion> findByModelIdAndVersion(UUID modelId, String version);
    Optional<AiModelVersion> findTopByModelIdAndStatusOrderByCreatedAtDesc(UUID modelId, String status);
    long countByModelId(UUID modelId);
    List<AiModelVersion> findByModelIdAndStatus(UUID modelId, String status);
}