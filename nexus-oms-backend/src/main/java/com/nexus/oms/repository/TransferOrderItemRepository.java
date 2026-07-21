package com.nexus.oms.repository;

import com.nexus.oms.entity.NxTransferOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransferOrderItemRepository extends JpaRepository<NxTransferOrderItem, UUID> {
    List<NxTransferOrderItem> findByTransferOrderId(UUID transferOrderId);
    List<NxTransferOrderItem> findBySku(String sku);
}
