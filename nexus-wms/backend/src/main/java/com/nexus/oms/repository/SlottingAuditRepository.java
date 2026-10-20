package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSlottingAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SlottingAuditRepository extends JpaRepository<NxSlottingAudit, UUID> {

    List<NxSlottingAudit> findByTenantId(UUID tenantId);

    List<NxSlottingAudit> findByWarehouseId(UUID warehouseId);

    List<NxSlottingAudit> findByWarehouseIdAndSku(UUID warehouseId, String sku);

    List<NxSlottingAudit> findByWarehouseIdAndCreatedAtBetween(UUID warehouseId, LocalDateTime from, LocalDateTime to);
}
