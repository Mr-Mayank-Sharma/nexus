package com.nexus.oms.repository;

import com.nexus.oms.entity.NxRateShoppingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RateShoppingLogRepository extends JpaRepository<NxRateShoppingLog, UUID> {
    Page<NxRateShoppingLog> findByTenantId(UUID tenantId, Pageable pageable);
}
