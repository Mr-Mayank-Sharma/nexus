package com.nexus.oms.repository;

import com.nexus.oms.entity.NxShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<NxShipment, UUID> {

    Optional<NxShipment> findByTrackingNumber(String trackingNumber);

    List<NxShipment> findByOrderId(UUID orderId);

    List<NxShipment> findByTenantId(UUID tenantId);

    @Query(value = "SELECT * FROM nx_shipments WHERE tenant_id = :tenantId " +
            "AND (CAST(id AS varchar) LIKE CONCAT('%', :q, '%') OR CAST(order_id AS varchar) LIKE CONCAT('%', :q, '%') " +
            "OR LOWER(COALESCE(tracking_number,'')) LIKE CONCAT('%', LOWER(:q), '%')) " +
            "ORDER BY created_at DESC LIMIT 20", nativeQuery = true)
    List<NxShipment> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
