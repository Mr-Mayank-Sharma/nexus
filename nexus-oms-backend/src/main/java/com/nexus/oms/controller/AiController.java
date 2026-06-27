package com.nexus.oms.controller;

import com.nexus.oms.ai.AiService;
import com.nexus.oms.dto.AllocationResponse;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.dto.InventoryRecommendation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/predict")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/carrier")
    public ResponseEntity<ApiResponse<AllocationResponse>> predictCarrier(@RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(ApiResponse.success(aiService.callCarrierAi(input)));
    }

    @PostMapping("/demand")
    public ResponseEntity<ApiResponse<DemandForecastResponse>> predictDemand(@RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(ApiResponse.success(aiService.callDemandAi(input)));
    }

    @PostMapping("/inventory")
    public ResponseEntity<ApiResponse<InventoryRecommendation>> predictInventory(@RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(ApiResponse.success(aiService.callInventoryAi(input)));
    }
}
