package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final DashboardService dashboardService;

    public AnalyticsController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getKPIs(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getKPIs(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActivity() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getActivity(TenantContext.getCurrentTenantId())));
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

    @GetMapping("/cost-breakdown")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCostBreakdown() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "shipping", 45230,
                "labor", 28300,
                "packaging", 12400,
                "warehouse", 18700,
                "returns", 5600
        )));
    }

    @GetMapping("/lanes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLanePerformance() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "lanes", List.of(
                        Map.of("origin", "NYC", "destination", "LAX", "volume", 120, "onTime", 0.95),
                        Map.of("origin", "CHI", "destination", "ATL", "volume", 85, "onTime", 0.92),
                        Map.of("origin", "LAX", "destination", "SEA", "volume", 65, "onTime", 0.98)
                )
        )));
    }

    @GetMapping("/returns")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReturnsAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalReturns", 45,
                "returnRate", 0.032,
                "avgRefundAmount", 89.50,
                "topReasons", List.of(
                        Map.of("reason", "Defective", "count", 18),
                        Map.of("reason", "Wrong item", "count", 12),
                        Map.of("reason", "Not as described", "count", 8)
                )
        )));
    }
}
