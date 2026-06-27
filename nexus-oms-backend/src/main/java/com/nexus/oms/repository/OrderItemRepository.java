package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<NxOrderItem, UUID> {

    List<NxOrderItem> findByOrderId(UUID orderId);

    void deleteByOrderId(UUID orderId);
}
