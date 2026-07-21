package com.nexus.oms.repository;

import com.nexus.oms.entity.NxShippingLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShippingLabelRepository extends JpaRepository<NxShippingLabel, UUID> {
    List<NxShippingLabel> findByTenantId(UUID tenantId);
    List<NxShippingLabel> findByOrderId(UUID orderId);
    NxShippingLabel findByPickupOrderId(UUID pickupOrderId);
    List<NxShippingLabel> findByTenantIdAndStatus(UUID tenantId, String status);
    NxShippingLabel findByTrackingNumber(String trackingNumber);
}
