package com.nexus.oms.service.bigcommerce;

import com.nexus.oms.entity.NxBigCommerceConfig;
import com.nexus.oms.repository.NxBigCommerceConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class BigCommerceOrderStatusPushService {

    private static final Logger log = LoggerFactory.getLogger(BigCommerceOrderStatusPushService.class);

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;

    public BigCommerceOrderStatusPushService(BigCommerceClient bcClient,
                                             NxBigCommerceConfigRepository configRepository) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
    }

    public void pushOrderStatus(UUID tenantId, String externalId, String nexusStatus) {
        if (tenantId == null || externalId == null || nexusStatus == null) return;

        Integer bcStatusId = mapNexusStatusToBigCommerce(nexusStatus);
        if (bcStatusId == null) {
            // Deliberate skip, not a silent no-op. PARTIALLY_ALLOCATED and FAILED are internal
            // allocation outcomes with no BigCommerce equivalent, and every status_id we could
            // pick would misrepresent the order — mapping FAILED to CANCELLED (5) in particular
            // would cancel a live customer order in the storefront. The state stays in the OMS
            // instead, where it is visible on the dashboard, the Orders UI, the AI agent queue
            // and the backorder fulfillment exception. Logged so the omission is observable.
            log.info("No BigCommerce status mapping for '{}' — push intentionally skipped for externalId {}. "
                    + "Internal-only state; see docs/hotwax/nexus-order-routing-comparison.md.", nexusStatus, externalId);
            return;
        }

        Optional<NxBigCommerceConfig> configOpt = configRepository.findByTenantIdAndIsActiveTrue(tenantId);
        if (configOpt.isEmpty()) return;

        NxBigCommerceConfig config = configOpt.get();
        String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();

        try {
            bcClient.updateOrderStatus(apiPath, config.getAccessToken(), Integer.parseInt(externalId), bcStatusId);
            log.info("Pushed order status {} -> BC status {} for externalId {}", nexusStatus, bcStatusId, externalId);
        } catch (Exception e) {
            log.warn("Failed to push order status to BigCommerce for externalId {}: {}", externalId, e.getMessage());
        }
    }

    private Integer mapNexusStatusToBigCommerce(String nexusStatus) {
        return switch (nexusStatus.toUpperCase()) {
            case "PENDING" -> 1;
            case "CONFIRMED", "ALLOCATED" -> 11;
            case "PICKING" -> 8;
            case "PACKING" -> 9;
            case "SHIPPED" -> 2;
            case "DELIVERED" -> 10;
            case "CANCELLED" -> 5;
            // Internal allocation outcomes (added with the T-17 stock-aware allocation work).
            // Listed explicitly, and deliberately unmapped, so the omission is greppable rather
            // than an accident of the default branch. BigCommerce has no partial-fulfilment or
            // allocation-failure status; forcing one of the IDs above onto these states would
            // tell the storefront the order is fully handled (or worse, cancelled) when it is
            // short. There is no authoritative status_id contract in this repo to map them to.
            // Revisit only with confirmed IDs from the store's BigCommerce status list.
            case "PARTIALLY_ALLOCATED", "FAILED" -> null;
            default -> null;
        };
    }
}
