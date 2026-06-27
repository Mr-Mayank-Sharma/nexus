package com.nexus.oms.repository;

import com.nexus.oms.entity.NxShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<NxShipment, UUID> {

    Optional<NxShipment> findByTrackingNumber(String trackingNumber);

    List<NxShipment> findByOrderId(UUID orderId);

    List<NxShipment> findByTenantId(UUID tenantId);
}
