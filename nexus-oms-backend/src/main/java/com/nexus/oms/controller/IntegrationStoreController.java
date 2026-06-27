package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.IntegrationStoreRequest;
import com.nexus.oms.dto.StoreSyncStatus;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxIntegrationStoreSetting;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/integration-stores")
public class IntegrationStoreController {

    private final IntegrationStoreService integrationStoreService;

    public IntegrationStoreController(IntegrationStoreService integrationStoreService) {
        this.integrationStoreService = integrationStoreService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NxIntegrationStore>>> getStores(
            @RequestParam(required = false) String platform) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationStoreService.getStores(TenantContext.getCurrentTenantId(), platform)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxIntegrationStore>> getStore(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationStoreService.getStore(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxIntegrationStore>> createStore(
            @Valid @RequestBody IntegrationStoreRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationStoreService.createStore(TenantContext.getCurrentTenantId(), request),
                "Store created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxIntegrationStore>> updateStore(
            @PathVariable UUID id,
            @Valid @RequestBody IntegrationStoreRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationStoreService.updateStore(id, request), "Store updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStore(@PathVariable UUID id) {
        integrationStoreService.deleteStore(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Store deleted"));
    }

    @GetMapping("/{id}/settings")
    public ResponseEntity<ApiResponse<List<NxIntegrationStoreSetting>>> getSettings(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationStoreService.getSettings(id)));
    }

    @GetMapping("/{id}/sync-status")
    public ResponseEntity<ApiResponse<StoreSyncStatus>> getSyncStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationStoreService.getSyncStatus(id)));
    }
}
