package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ManifestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Manifests", description = "Shipping manifest APIs")
@RestController
@RequestMapping("/manifests")
public class ManifestController {

    private final ManifestService manifestService;

    public ManifestController(ManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @Operation(summary = "List manifests")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllManifests() {
        return ResponseEntity.ok(ApiResponse.success(
                manifestService.getManifests(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Create a manifest")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createManifest(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(
                manifestService.createManifest(TenantContext.getCurrentTenantId(), request), "Manifest created"));
    }

    @Operation(summary = "Update a manifest")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateManifest(
            @PathVariable UUID id, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(
                manifestService.updateManifest(id, request), "Manifest updated"));
    }
}
