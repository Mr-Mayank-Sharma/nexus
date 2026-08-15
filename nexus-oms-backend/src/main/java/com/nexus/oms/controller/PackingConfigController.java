package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxBoxTemplate;
import com.nexus.oms.entity.NxKitTemplate;
import com.nexus.oms.repository.BoxTemplateRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.BoxRecommendationService;
import com.nexus.oms.service.KittingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Packing Configuration", description = "Box catalog, box recommendation and kitting APIs")
@RestController
@RequestMapping("/packing")
public class PackingConfigController {

    private final BoxTemplateRepository boxTemplateRepository;
    private final BoxRecommendationService boxRecommendationService;
    private final KittingService kittingService;

    public PackingConfigController(BoxTemplateRepository boxTemplateRepository,
                                   BoxRecommendationService boxRecommendationService,
                                   KittingService kittingService) {
        this.boxTemplateRepository = boxTemplateRepository;
        this.boxRecommendationService = boxRecommendationService;
        this.kittingService = kittingService;
    }

    @Operation(summary = "List active box templates")
    @GetMapping("/boxes")
    public ResponseEntity<ApiResponse<List<NxBoxTemplate>>> getBoxes() {
        return ResponseEntity.ok(ApiResponse.success(
                boxRecommendationService.getActiveBoxes(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Create a box template")
    @PostMapping("/boxes")
    public ResponseEntity<ApiResponse<NxBoxTemplate>> createBox(@Valid @RequestBody NxBoxTemplate box) {
        box.setTenantId(TenantContext.getCurrentTenantId());
        box.setIsActive(box.getIsActive() == null ? true : box.getIsActive());
        return ResponseEntity.ok(ApiResponse.success(boxTemplateRepository.save(box), "Box template created"));
    }

    @Operation(summary = "Delete a box template")
    @DeleteMapping("/boxes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBox(@PathVariable UUID id) {
        boxTemplateRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Box template deleted"));
    }

    @Operation(summary = "Recommend a box for given dimensions")
    @PostMapping("/boxes/recommend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recommend(@RequestParam double volume,
                                                                      @RequestParam(required = false, defaultValue = "0") double weight,
                                                                      @RequestParam(required = false, defaultValue = "1") int itemCount) {
        return ResponseEntity.ok(ApiResponse.success(
                boxRecommendationService.recommend(TenantContext.getCurrentTenantId(), volume, weight, itemCount)));
    }

    @Operation(summary = "List kit templates")
    @GetMapping("/kits")
    public ResponseEntity<ApiResponse<List<NxKitTemplate>>> getKits() {
        return ResponseEntity.ok(ApiResponse.success(kittingService.listTemplates(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Create a kit template")
    @PostMapping("/kits")
    public ResponseEntity<ApiResponse<NxKitTemplate>> createKit(@Valid @RequestBody NxKitTemplate kit) {
        return ResponseEntity.ok(ApiResponse.success(
                kittingService.createTemplate(TenantContext.getCurrentTenantId(), kit), "Kit template created"));
    }

    @Operation(summary = "Update a kit template")
    @PutMapping("/kits/{id}")
    public ResponseEntity<ApiResponse<NxKitTemplate>> updateKit(@PathVariable UUID id,
                                                                @Valid @RequestBody NxKitTemplate kit) {
        return ResponseEntity.ok(ApiResponse.success(
                kittingService.updateTemplate(TenantContext.getCurrentTenantId(), id, kit), "Kit template updated"));
    }

    @Operation(summary = "Delete a kit template")
    @DeleteMapping("/kits/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteKit(@PathVariable UUID id) {
        kittingService.deleteTemplate(TenantContext.getCurrentTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Kit template deleted"));
    }

    @Operation(summary = "Explode a kit order into component lines (no persistence)")
    @GetMapping("/kits/explode/{orderId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> explodePlan(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                kittingService.explodePlan(TenantContext.getCurrentTenantId(), orderId)));
    }

    @Operation(summary = "Explode a kit order into component lines and persist them")
    @PostMapping("/kits/explode/{orderId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> explodeAndPersist(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                kittingService.explodeAndPersist(TenantContext.getCurrentTenantId(), orderId), "Kit exploded"));
    }
}
