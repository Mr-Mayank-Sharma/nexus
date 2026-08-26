package com.nexus.oms.controller;

import com.nexus.oms.ai.AiService;
import com.nexus.oms.dto.AllocationResponse;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.dto.InventoryRecommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Legacy AI bridge endpoints (Flask :5000/:5001).
 *
 * P1.3 contract: these are EXPLICIT AI endpoints — when the model backend
 * is unavailable they return 503 with a clear message instead of silently
 * fabricating "STANDARD" predictions.
 */
@RestController
@RequestMapping("/ai/predict")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/carrier")
    public ResponseEntity<ApiResponse<AllocationResponse>> predictCarrier(@RequestBody Map<String, Object> input) {
        try {
            return ResponseEntity.ok(ApiResponse.success(aiService.callCarrierAi(input)));
        } catch (Exception e) {
            return unavailable("carrier", e);
        }
    }

    @PostMapping("/demand")
    public ResponseEntity<ApiResponse<DemandForecastResponse>> predictDemand(@RequestBody Map<String, Object> input) {
        try {
            return ResponseEntity.ok(ApiResponse.success(aiService.callDemandAi(input)));
        } catch (Exception e) {
            return unavailable("demand", e);
        }
    }

    @PostMapping("/inventory")
    public ResponseEntity<ApiResponse<InventoryRecommendation>> predictInventory(@RequestBody Map<String, Object> input) {
        try {
            return ResponseEntity.ok(ApiResponse.success(aiService.callInventoryAi(input)));
        } catch (Exception e) {
            return unavailable("inventory", e);
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> unavailable(String endpoint, Exception e) {
        log.warn("Legacy AI endpoint /{} unavailable: {}", endpoint, e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Legacy AI service unavailable for '" + endpoint
                        + "'. No prediction was fabricated. Cause: " + e.getMessage()));
    }
}
