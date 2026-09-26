package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxCarrierLabelConfig;
import com.nexus.oms.service.CarrierLabelConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/carrier-label-config")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CarrierLabelConfigController {

    private final CarrierLabelConfigService configService;

    public CarrierLabelConfigController(CarrierLabelConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NxCarrierLabelConfig>>> getConfigs() {
        return ResponseEntity.ok(ApiResponse.success(configService.getConfigs()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxCarrierLabelConfig>> upsertConfig(@RequestBody NxCarrierLabelConfig config) {
        return ResponseEntity.ok(ApiResponse.success(configService.upsertConfig(config), "Carrier label config saved"));
    }

    @GetMapping("/adapters")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableAdapters() {
        return ResponseEntity.ok(ApiResponse.success(configService.getAvailableAdapters()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCarrierLabelConfig>> getConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(configService.getConfig(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable UUID id) {
        configService.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Carrier label config deleted"));
    }
}