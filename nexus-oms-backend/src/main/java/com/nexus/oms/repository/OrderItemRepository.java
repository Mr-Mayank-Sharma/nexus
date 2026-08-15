package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<NxOrderItem, UUID> {

    List<NxOrderItem> findByOrderId(UUID orderId);

    List<NxOrderItem> findByOrderIdIn(List<UUID> orderIds);

    void deleteByOrderId(UUID orderId);

    @Query(value = """
        SELECT oi.sku, CAST(o.created_at AS date) AS day, SUM(oi.quantity) AS demand
        FROM nx_order_items oi
        JOIN nx_orders o ON o.id = oi.order_id
        WHERE o.tenant_id = :tenantId
          AND o.created_at >= :from
          AND (o.status NOT ILIKE '%CANCEL%' AND o.status NOT ILIKE '%FAILED%')
        GROUP BY oi.sku, CAST(o.created_at AS date)
        """, nativeQuery = true)
    List<Object[]> aggregateDemandBySkuAndDay(@Param("tenantId") UUID tenantId, @Param("from") LocalDateTime from);
}
