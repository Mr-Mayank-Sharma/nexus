package com.nexus.oms.repository;

import com.nexus.oms.entity.NxFulfillmentReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FulfillmentReconciliationRepository extends JpaRepository<NxFulfillmentReconciliation, UUID> {

    Optional<NxFulfillmentReconciliation> findByTenantIdAndShopifyFulfillmentId(UUID tenantId, String shopifyFulfillmentId);

    List<NxFulfillmentReconciliation> findByTenantIdAndOrderIdOrderByCreatedAtDesc(UUID tenantId, UUID orderId);

    List<NxFulfillmentReconciliation> findByTenantIdAndResolutionInOrderByCreatedAtDesc(UUID tenantId, List<String> resolutions);
}