package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.RateShoppingRequest;
import com.nexus.oms.dto.RateShoppingResult;
import com.nexus.oms.service.RateShoppingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rate-shopping")
public class RateShoppingController {

    private final RateShoppingService rateShoppingService;

    public RateShoppingController(RateShoppingService rateShoppingService) {
        this.rateShoppingService = rateShoppingService;
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
