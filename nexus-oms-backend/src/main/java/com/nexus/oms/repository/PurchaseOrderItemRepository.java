package com.nexus.oms.repository;

import com.nexus.oms.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

    List<PurchaseOrderItem> findByPoId(UUID poId);

    Optional<PurchaseOrderItem> findByPoIdAndSku(UUID poId, String sku);
}
