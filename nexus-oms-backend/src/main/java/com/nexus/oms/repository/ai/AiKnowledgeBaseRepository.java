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

public interface AiKnowledgeBaseRepository extends JpaRepository<AiKnowledgeBase, UUID> {
    Page<AiKnowledgeBase> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<AiKnowledgeBase> findByTenantIdAndName(UUID tenantId, String name);
    List<AiKnowledgeBase> findByStatus(String status);
}