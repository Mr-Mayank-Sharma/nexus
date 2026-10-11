package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxChannelLocationMapping;
import com.nexus.oms.entity.NxInventoryPublishLine;
import com.nexus.oms.entity.NxInventoryPublishRun;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxInventoryPublishLineRepository;
import com.nexus.oms.repository.NxInventoryPublishRunRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.shopify.InventoryChannelMappingService;
import com.nexus.oms.service.shopify.InventoryChannelMappingService.MappingInput;
import com.nexus.oms.service.shopify.InventoryChannelMappingService.ShopifyLocation;
import com.nexus.oms.service.shopify.ShopifyInventorySyncService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Location mapping, publishing and the reconciliation report for a Shopify store's inventory. */
@RestController
@RequestMapping("/shopify/stores/{storeId}/inventory")
public class ShopifyInventoryController {

    private final ShopifyInventorySyncService publishService;
    private final InventoryChannelMappingService mappingService;
    private final NxInventoryPublishRunRepository runRepository;
    private final NxInventoryPublishLineRepository lineRepository;
    private final IntegrationStoreService storeService;

    public ShopifyInventoryController(ShopifyInventorySyncService publishService,
                                      InventoryChannelMappingService mappingService,
                                      NxInventoryPublishRunRepository runRepository,
                                      NxInventoryPublishLineRepository lineRepository,
                                      IntegrationStoreService storeService) {
        this.publishService = publishService;
        this.mappingService = mappingService;
        this.runRepository = runRepository;
        this.lineRepository = lineRepository;
        this.storeService = storeService;
    }

    @GetMapping("/shopify-locations")
    public ResponseEntity<ApiResponse<List<ShopifyLocation>>> shopifyLocations(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(mappingService.shopifyLocations(storeId)));
    }

    @GetMapping("/location-mappings")
    public ResponseEntity<ApiResponse<List<NxChannelLocationMapping>>> mappings(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(mappingService.list(storeId)));
    }

    @PutMapping("/location-mappings")
    public ResponseEntity<ApiResponse<List<NxChannelLocationMapping>>> replaceMappings(
            @PathVariable UUID storeId, @RequestBody List<MappingInput> mappings) {
        return ResponseEntity.ok(ApiResponse.success(mappingService.replace(storeId, mappings), "Location mappings saved"));
    }

    /** Dry run by default: calculating is always safe, writing to Shopify must be asked for. */
    @PostMapping("/publish")
    public ResponseEntity<ApiResponse<NxInventoryPublishRun>> publish(
            @PathVariable UUID storeId, @RequestParam(defaultValue = "true") boolean dryRun) {
        NxInventoryPublishRun run = publishService.publish(storeId, dryRun, TenantContext.getCurrentUsername());
        return ResponseEntity.ok(ApiResponse.success(run, run.getMessage()));
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<NxInventoryPublishRun>>> runs(
            @PathVariable UUID storeId, @RequestParam(defaultValue = "20") int limit) {
        storeService.getStore(storeId);
        return ResponseEntity.ok(ApiResponse.success(runRepository
                .findByIntegrationStoreIdOrderByStartedAtDesc(storeId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))));
    }

    @GetMapping("/runs/{runId}/lines")
    public ResponseEntity<ApiResponse<List<NxInventoryPublishLine>>> lines(
            @PathVariable UUID storeId, @PathVariable UUID runId,
            @RequestParam(defaultValue = "500") int limit) {
        NxInventoryPublishRun run = runRepository.findById(runId)
                .filter(r -> r.getIntegrationStoreId().equals(storeId))
                .orElseThrow(() -> new ResourceNotFoundException("InventoryPublishRun", runId));
        return ResponseEntity.ok(ApiResponse.success(lineRepository.findByRunIdOrderBySkuAscExternalLocationIdAsc(
                run.getId(), PageRequest.of(0, Math.min(Math.max(limit, 1), 5000)))));
    }
}
