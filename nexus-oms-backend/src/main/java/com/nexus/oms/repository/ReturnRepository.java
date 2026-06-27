package com.nexus.oms.repository;

import com.nexus.oms.entity.NxReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReturnRepository extends JpaRepository<NxReturn, UUID> {

    List<NxReturn> findByOrderId(UUID orderId);

    List<NxReturn> findByTenantId(UUID tenantId);

    List<NxReturn> findByTenantIdAndStatus(UUID tenantId, String status);
}
