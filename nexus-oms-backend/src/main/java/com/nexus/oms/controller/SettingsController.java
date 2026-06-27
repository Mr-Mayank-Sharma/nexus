package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.service.CompanySettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final CompanySettingsService companySettingsService;

    public SettingsController(CompanySettingsService companySettingsService) {
        this.companySettingsService = companySettingsService;
    }

    @GetMapping("/company")
    public ResponseEntity<ApiResponse<CompanySettings>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(companySettingsService.getSettings()));
    }

    @PutMapping("/company")
    public ResponseEntity<ApiResponse<CompanySettings>> updateSettings(@RequestBody CompanySettings companySettings) {
        return ResponseEntity.ok(ApiResponse.success(
                companySettingsService.updateSettings(companySettings), "Settings updated"));
    }

    @PutMapping("/company/feature-flags")
    public ResponseEntity<ApiResponse<CompanySettings>> updateFeatureFlags(@RequestBody Map<String, Object> featureFlags) {
        return ResponseEntity.ok(ApiResponse.success(
                companySettingsService.updateFeatureFlags(featureFlags), "Feature flags updated"));
    }

    @GetMapping("/company/feature-flags/{name}")
    public ResponseEntity<ApiResponse<Object>> getFeatureFlag(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(companySettingsService.getFeatureFlag(name)));
    }

    @PutMapping("/company/security-policy")
    public ResponseEntity<ApiResponse<CompanySettings>> updateSecurityPolicy(@RequestBody Map<String, Object> securityPolicy) {
        return ResponseEntity.ok(ApiResponse.success(
                companySettingsService.updateSecurityPolicy(securityPolicy), "Security policy updated"));
    }

    @GetMapping("/company/feature-flags/{name}/enabled")
    public ResponseEntity<ApiResponse<Boolean>> isFeatureEnabled(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(companySettingsService.isFeatureEnabled(name)));
    }
}
