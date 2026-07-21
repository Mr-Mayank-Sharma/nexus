package com.nexus.oms.controller;

import com.nexus.oms.entity.NxPromotion;
import com.nexus.oms.service.PromotionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/promotions")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    // ─── CRUD ───────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<NxPromotion> createPromotion(@RequestBody NxPromotion promotion) {
        return ResponseEntity.ok(promotionService.createPromotion(promotion));
    }

    @GetMapping
    public ResponseEntity<List<NxPromotion>> getPromotions() {
        return ResponseEntity.ok(promotionService.getPromotions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NxPromotion> getPromotion(@PathVariable UUID id) {
        return ResponseEntity.ok(promotionService.getPromotion(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NxPromotion> updatePromotion(@PathVariable UUID id, @RequestBody NxPromotion promotion) {
        return ResponseEntity.ok(promotionService.updatePromotion(id, promotion));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromotion(@PathVariable UUID id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Promotion Application ──────────────────────────────────────────

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCoupon(
            @RequestParam String couponCode,
            @RequestParam(required = false) UUID customerId,
            @RequestParam BigDecimal orderTotal) {
        return ResponseEntity.ok(promotionService.validateCoupon(couponCode, customerId, orderTotal));
    }

    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculateDiscount(
            @RequestParam String couponCode,
            @RequestParam(required = false) UUID customerId,
            @RequestParam BigDecimal orderTotal,
            @RequestParam(defaultValue = "1") int quantity) {
        return ResponseEntity.ok(promotionService.calculateDiscount(couponCode, customerId, orderTotal, quantity));
    }

    @PostMapping("/{id}/record")
    public ResponseEntity<Void> recordUsage(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String couponCode,
            @RequestParam BigDecimal discountAmount,
            @RequestParam BigDecimal orderTotal) {
        promotionService.recordUsage(id, orderId, customerId, couponCode, discountAmount, orderTotal);
        return ResponseEntity.ok().build();
    }

    // ─── Analytics ──────────────────────────────────────────────────────

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getPromotionStats(@PathVariable UUID id) {
        return ResponseEntity.ok(promotionService.getPromotionStats(id));
    }

    @GetMapping("/active-for-order")
    public ResponseEntity<List<Map<String, Object>>> getActivePromotionsForOrder(
            @RequestParam BigDecimal orderTotal,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(required = false) String channel) {
        return ResponseEntity.ok(promotionService.getActivePromotionsForOrder(orderTotal, quantity, channel));
    }
}
