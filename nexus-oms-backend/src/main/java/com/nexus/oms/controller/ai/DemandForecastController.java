package com.nexus.oms.controller.ai;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ai.DemandForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Demand Forecasting", description = "Java-native demand forecasting on real order history")
@RestController
@RequestMapping("/api/ai/forecast")
public class DemandForecastController {

    private final DemandForecastService demandForecastService;

    public DemandForecastController(DemandForecastService demandForecastService) {
        this.demandForecastService = demandForecastService;
    }

    @Operation(summary = "Forecast demand for the next N days from real order history")
    @GetMapping
    public ResponseEntity<ApiResponse<DemandForecastResponse>> forecast(
            @RequestParam(defaultValue = "30") int horizonDays) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(demandForecastService.forecast(tenantId, horizonDays)));
    }

    @Operation(summary = "Evaluate forecast accuracy (WAPE) on a holdout window of real order history")
    @GetMapping("/evaluate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> evaluate() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(demandForecastService.evaluate(tenantId)));
    }
}
