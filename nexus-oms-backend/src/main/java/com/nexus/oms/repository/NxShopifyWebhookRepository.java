package com.nexus.oms.repository;

import com.nexus.oms.entity.NxShopifyWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NxShopifyWebhookRepository extends JpaRepository<NxShopifyWebhook, UUID> {
    List<NxShopifyWebhook> findByTenantId(UUID tenantId);
    List<NxShopifyWebhook> findByStoreId(UUID storeId);
}
