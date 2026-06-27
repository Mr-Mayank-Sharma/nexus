package com.nexus.oms.repository;

import com.nexus.oms.entity.NxBigCommerceWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NxBigCommerceWebhookRepository extends JpaRepository<NxBigCommerceWebhook, UUID> {
    List<NxBigCommerceWebhook> findByTenantId(UUID tenantId);
    List<NxBigCommerceWebhook> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
