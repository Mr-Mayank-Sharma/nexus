package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.service.netsuite.NetSuiteClient;
import com.nexus.oms.service.netsuite.NetSuiteInventoryBaselineService;
import com.nexus.oms.service.shopify.InventoryChannelMappingService;
import com.nexus.oms.service.shopify.InventoryChannelMappingService.MappingInput;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NetSuite for one integration store. Reads only. Under {@code /integration-stores} so it inherits the same
 * permission and edition rules as every other integration screen.
 *
 * <p>Store settings used: {@code account_id}, {@code consumer_key}, {@code consumer_secret}, {@code token_id},
 * {@code token_secret}, and optionally {@code inventory_query}. Secrets are sealed at rest and never returned.
 */
@RestController
@RequestMapping("/integration-stores/{storeId}/netsuite")
public class NetSuiteController {

    private final NetSuiteInventoryBaselineService baseline;
    private final NetSuiteClient client;
    private final InventoryChannelMappingService mappings;

    public NetSuiteController(NetSuiteInventoryBaselineService baseline, NetSuiteClient client,
                              InventoryChannelMappingService mappings) {
        this.baseline = baseline;
        this.client = client;
        this.mappings = mappings;
    }

    /** Proves the account id and token values work. A failure comes back as the reason, not as a fake success. */
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(@PathVariable UUID storeId) {
        client.testConnection(baseline.credentials(storeId));
        return ResponseEntity.ok(ApiResponse.success(Map.of("connected", true), "NetSuite accepted the credentials"));
    }

    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> locations(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(
                client.query(baseline.credentials(storeId), "SELECT id, name FROM location ORDER BY name")));
    }

    @GetMapping("/location-mappings")
    public ResponseEntity<ApiResponse<?>> getMappings(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(mappings.list(storeId)));
    }

    @PutMapping("/location-mappings")
    public ResponseEntity<ApiResponse<?>> putMappings(@PathVariable UUID storeId, @RequestBody List<MappingInput> inputs) {
        return ResponseEntity.ok(ApiResponse.success(mappings.replace(storeId, inputs), "Location mappings saved"));
    }

    /** Dry run unless {@code apply=true}; existing Nexus stock is kept unless {@code overwrite=true}. */
    @PostMapping("/inventory/baseline")
    public ResponseEntity<ApiResponse<NetSuiteInventoryBaselineService.Report>> baseline(
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "false") boolean apply,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        var report = baseline.run(storeId, apply, overwrite);
        return ResponseEntity.ok(ApiResponse.success(report, report.message()));
    }
}
