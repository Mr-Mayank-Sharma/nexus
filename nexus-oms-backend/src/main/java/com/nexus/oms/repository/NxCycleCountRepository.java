package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCycleCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NxCycleCountRepository extends JpaRepository<NxCycleCount, UUID> {
    Page<NxCycleCount> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxCycleCount> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
