package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.RateShoppingRequest;
import com.nexus.oms.dto.RateShoppingResult;
import com.nexus.oms.service.RateCacheService;
import com.nexus.oms.service.RateShoppingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/rate-shopping")
public class RateShoppingController {

    private final RateShoppingService rateShoppingService;
    private final RateCacheService rateCacheService;

    public RateShoppingController(RateShoppingService rateShoppingService,
                                   RateCacheService rateCacheService) {
        this.rateShoppingService = rateShoppingService;
        this.rateCacheService = rateCacheService;
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheStats() {
        return ResponseEntity.ok(ApiResponse.success(rateCacheService.getStats(), "Cache statistics"));
    }

    @PostMapping("/shop")
    public ResponseEntity<ApiResponse<RateShoppingResult>> shopRates(
            @Valid @RequestBody RateShoppingRequest request) {
        RateShoppingResult result = rateShoppingService.shopRates(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Rate shopping completed"));
    }

    @PostMapping("/best")
    public ResponseEntity<ApiResponse<RateShoppingResult>> getBestRate(
            @Valid @RequestBody RateShoppingRequest request) {
        RateShoppingResult result = rateShoppingService.getBestRate(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Best rate selected"));
    }
}
