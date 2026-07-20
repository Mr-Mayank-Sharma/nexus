package com.nexus.oms.repository;

import com.nexus.oms.entity.NxWave;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WaveRepository extends JpaRepository<NxWave, UUID> {

    List<NxWave> findByTenantId(UUID tenantId);

    List<NxWave> findByTenantIdAndStatus(UUID tenantId, String status);

    List<NxWave> findByWarehouseIdAndStatus(UUID warehouseId, String status);

    List<NxWave> findByTenantIdAndPriority(UUID tenantId, String priority);
}
