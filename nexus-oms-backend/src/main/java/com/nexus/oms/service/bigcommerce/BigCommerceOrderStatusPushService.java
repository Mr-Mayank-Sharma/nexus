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
        if (bcStatusId == null) return;

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
            default -> null;
        };
    }
}
