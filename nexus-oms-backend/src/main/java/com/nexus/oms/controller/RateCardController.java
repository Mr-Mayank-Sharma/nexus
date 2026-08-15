package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxRateCard;
import com.nexus.oms.service.RateCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "3PL Rate Cards", description = "Multi-client rate card management for 3PL billing")
@RestController
@RequestMapping("/rate-cards")
public class RateCardController {

    private final RateCardService rateCardService;

    public RateCardController(RateCardService rateCardService) {
        this.rateCardService = rateCardService;
    }

    @Operation(summary = "Create a rate card")
    @PostMapping
    public ResponseEntity<ApiResponse<NxRateCard>> create(@RequestBody NxRateCard card) {
        return ResponseEntity.ok(ApiResponse.success(rateCardService.createRateCard(card), "Rate card created"));
    }

    @Operation(summary = "List rate cards (optionally filtered by client)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxRateCard>>> list(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(rateCardService.getRateCards(clientId)));
    }

    @Operation(summary = "Get a rate card")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxRateCard>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(rateCardService.getRateCard(id)));
    }

    @Operation(summary = "Update a rate card")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxRateCard>> update(@PathVariable UUID id, @RequestBody NxRateCard card) {
        return ResponseEntity.ok(ApiResponse.success(rateCardService.updateRateCard(id, card), "Rate card updated"));
    }

    @Operation(summary = "Delete a rate card")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        rateCardService.deleteRateCard(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Rate card deleted"));
    }
}
