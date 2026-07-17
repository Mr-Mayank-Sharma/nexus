package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.service.CompanySettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Settings", description = "Settings management APIs")
@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final CompanySettingsService companySettingsService;

    public SettingsController(CompanySettingsService companySettingsService) {
        this.companySettingsService = companySettingsService;
    }

    @Operation(summary = "Get settings dashboard")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettingsDashboard() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @Operation(summary = "Get company settings")
    @GetMapping("/company")
    public ResponseEntity<ApiResponse<CompanySettings>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(companySettingsService.getSettings()));
    }

    @Operation(summary = "Update company settings")
    @PutMapping("/company")
    public ResponseEntity<ApiResponse<CompanySettings>> updateSettings(@Valid @RequestBody CompanySettings companySettings) {
        return ResponseEntity.ok(ApiResponse.success(
                companySettingsService.updateSettings(companySettings), "Settings updated"));
    }

    @Operation(summary = "Update feature flags")
    @PutMapping("/company/feature-flags")
    public ResponseEntity<ApiResponse<CompanySettings>> updateFeatureFlags(@RequestBody Map<String, Object> featureFlags) {
        return ResponseEntity.ok(ApiResponse.success(
                companySettingsService.updateFeatureFlags(featureFlags), "Feature flags updated"));
    }

    @Operation(summary = "Get a feature flag by name")
    @GetMapping("/company/feature-flags/{name}")
    public ResponseEntity<ApiResponse<Object>> getFeatureFlag(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(companySettingsService.getFeatureFlag(name)));
    }

    @Operation(summary = "Update security policy")
    @PutMapping("/company/security-policy")
    public ResponseEntity<ApiResponse<CompanySettings>> updateSecurityPolicy(@RequestBody Map<String, Object> securityPolicy) {
        return ResponseEntity.ok(ApiResponse.success(
                companySettingsService.updateSecurityPolicy(securityPolicy), "Security policy updated"));
    }

    @Operation(summary = "Check if a feature is enabled")
    @GetMapping("/company/feature-flags/{name}/enabled")
    public ResponseEntity<ApiResponse<Boolean>> isFeatureEnabled(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(companySettingsService.isFeatureEnabled(name)));
    }
}
