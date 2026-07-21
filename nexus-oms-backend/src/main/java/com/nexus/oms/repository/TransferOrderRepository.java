package com.nexus.oms.repository;

import com.nexus.oms.entity.NxTransferOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransferOrderRepository extends JpaRepository<NxTransferOrder, UUID> {
    List<NxTransferOrder> findByTenantId(UUID tenantId);
    List<NxTransferOrder> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxTransferOrder> findBySourceNodeId(UUID sourceNodeId);
    List<NxTransferOrder> findByDestinationNodeId(UUID destinationNodeId);
    List<NxTransferOrder> findByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);
    NxTransferOrder findByTransferNumber(String transferNumber);
}
