package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.entity.NxChannelLocationMapping;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.NxChannelLocationMappingRepository;
import com.nexus.oms.repository.WarehouseRepository;
import com.nexus.oms.service.IntegrationStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Maintains which Nexus node feeds which Shopify location. */
@Service
public class InventoryChannelMappingService {

    /** One mapping as sent by the UI. */
    public record MappingInput(UUID nodeId, String externalLocationId, String externalLocationName,
                               Boolean enabled, Integer bufferUnits, Integer contributionCap) {
    }

    /** A live Shopify location, for the mapping screen. */
    public record ShopifyLocation(String id, String name, boolean active) {
    }

    private final NxChannelLocationMappingRepository mappingRepository;
    private final WarehouseRepository warehouseRepository;
    private final IntegrationStoreService storeService;
    private final ShopifyClient shopifyClient;
    private final ShopifyTokenService tokenService;

    public InventoryChannelMappingService(NxChannelLocationMappingRepository mappingRepository,
                                          WarehouseRepository warehouseRepository,
                                          IntegrationStoreService storeService,
                                          ShopifyClient shopifyClient,
                                          ShopifyTokenService tokenService) {
        this.mappingRepository = mappingRepository;
        this.warehouseRepository = warehouseRepository;
        this.storeService = storeService;
        this.shopifyClient = shopifyClient;
        this.tokenService = tokenService;
    }

    public List<NxChannelLocationMapping> list(UUID storeId) {
        storeService.getStore(storeId);   // 404 for a store the caller cannot see
        return mappingRepository.findByIntegrationStoreId(storeId);
    }

    /** The store's locations as Shopify reports them right now. */
    public List<ShopifyLocation> shopifyLocations(UUID storeId) {
        storeService.getStore(storeId);
        JsonNode body = shopifyClient.getLocations(storeService.getSetting(storeId, "shop_domain"), tokenService.getAccessToken(storeId));
        List<ShopifyLocation> result = new ArrayList<>();
        JsonNode locations = body != null ? body.get("locations") : null;
        if (locations != null) {
            for (JsonNode l : locations) {
                result.add(new ShopifyLocation(l.path("id").asText(), l.path("name").asText(), l.path("active").asBoolean(true)));
            }
        }
        return result;
    }

    /**
     * Replaces the store's mappings. A node may appear once (its stock cannot feed two locations,
     * which would offer every unit twice); several nodes may share one Shopify location.
     */
    @Transactional
    public List<NxChannelLocationMapping> replace(UUID storeId, List<MappingInput> inputs) {
        NxIntegrationStore store = storeService.getStore(storeId);
        Set<UUID> seen = new HashSet<>();
        for (MappingInput in : inputs) {
            if (in.nodeId() == null) throw new BadRequestException("Every mapping needs a Nexus location (nodeId)");
            if (in.externalLocationId() == null || !in.externalLocationId().matches("\\d{1,20}")) {
                throw new BadRequestException("Shopify location ids are numeric; got '" + in.externalLocationId() + "'");
            }
            if (!seen.add(in.nodeId())) {
                throw new BadRequestException("A Nexus location can feed only one Shopify location per store; it appears twice");
            }
            if (in.bufferUnits() != null && in.bufferUnits() < 0) throw new BadRequestException("Buffer units cannot be negative");
            if (in.contributionCap() != null && in.contributionCap() < 0) throw new BadRequestException("A contribution cap cannot be negative");
        }
        Map<UUID, Warehouse> nodes = warehouseRepository.findAllById(seen).stream()
                .filter(w -> store.getTenantId().equals(w.getTenantId()))
                .collect(Collectors.toMap(Warehouse::getId, w -> w));
        for (UUID nodeId : seen) {
            if (!nodes.containsKey(nodeId)) throw new BadRequestException("Unknown Nexus location: " + nodeId);
        }
        mappingRepository.deleteByIntegrationStoreId(storeId);
        mappingRepository.flush();
        return mappingRepository.saveAll(inputs.stream().map(in -> NxChannelLocationMapping.builder()
                .tenantId(store.getTenantId()).integrationStoreId(storeId).nodeId(in.nodeId())
                .externalLocationId(in.externalLocationId()).externalLocationName(in.externalLocationName())
                .enabled(in.enabled() == null || in.enabled())
                .bufferUnits(in.bufferUnits() != null ? in.bufferUnits() : 0)
                .contributionCap(in.contributionCap())
                .build()).toList());
    }
}
