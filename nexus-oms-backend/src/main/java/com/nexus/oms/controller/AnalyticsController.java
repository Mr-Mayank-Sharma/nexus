package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final DashboardService dashboardService;

    public AnalyticsController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getKPIs(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/orders/velocity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderVelocity() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOrderVelocity()));
    }

    @GetMapping("/carrier-performance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCarrierPerformance() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalShipments", 150,
                "onTimeRate", 0.94,
                "avgDeliveryDays", 2.5
        )));
    }

    @GetMapping("/exceptions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExceptions() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getExceptions(TenantContext.getCurrentTenantId())));
    }
}
