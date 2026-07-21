package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPickupOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PickupOrderItemRepository extends JpaRepository<NxPickupOrderItem, UUID> {
    List<NxPickupOrderItem> findByPickupOrderId(UUID pickupOrderId);
    List<NxPickupOrderItem> findByPickupOrderIdAndStatus(UUID pickupOrderId, String status);
}
