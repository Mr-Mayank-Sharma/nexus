package com.nexus.oms.controller;

import com.nexus.oms.entity.NxShippingLabel;
import com.nexus.oms.service.ShippingLabelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/labels")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ShippingLabelController {

    private final ShippingLabelService shippingLabelService;

    public ShippingLabelController(ShippingLabelService shippingLabelService) {
        this.shippingLabelService = shippingLabelService;
    }

    @PostMapping
    public ResponseEntity<NxShippingLabel> generateLabel(@RequestBody NxShippingLabel label) {
        return ResponseEntity.ok(shippingLabelService.generateLabel(label));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<NxShippingLabel>> generateLabels(
            @RequestParam UUID orderId,
            @RequestParam String orderNumber,
            @RequestBody List<NxShippingLabel> labels) {
        return ResponseEntity.ok(shippingLabelService.generateLabelsForOrder(orderId, orderNumber, labels));
    }

    @PostMapping("/bopis")
    public ResponseEntity<NxShippingLabel> generateBopisLabel(
            @RequestParam UUID pickupOrderId,
            @RequestParam String orderNumber,
            @RequestParam String fromName,
            @RequestParam String fromAddress) {
        return ResponseEntity.ok(shippingLabelService.generateBopisLabel(pickupOrderId, orderNumber, fromName, fromAddress));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NxShippingLabel> getLabel(@PathVariable UUID id) {
        return ResponseEntity.ok(shippingLabelService.getLabel(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<NxShippingLabel>> getLabelsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(shippingLabelService.getLabelsByOrder(orderId));
    }

    @GetMapping("/pickup/{pickupOrderId}")
    public ResponseEntity<NxShippingLabel> getLabelByPickupOrder(@PathVariable UUID pickupOrderId) {
        NxShippingLabel label = shippingLabelService.getLabelByPickupOrder(pickupOrderId);
        return label != null ? ResponseEntity.ok(label) : ResponseEntity.notFound().build();
    }

    @GetMapping("/pending")
    public ResponseEntity<List<NxShippingLabel>> getPendingLabels() {
        return ResponseEntity.ok(shippingLabelService.getPendingLabels());
    }

    @PostMapping("/{id}/print")
    public ResponseEntity<NxShippingLabel> markPrinted(@PathVariable UUID id) {
        return ResponseEntity.ok(shippingLabelService.markPrinted(id));
    }

    @PostMapping("/{id}/attach")
    public ResponseEntity<NxShippingLabel> markAttached(@PathVariable UUID id) {
        return ResponseEntity.ok(shippingLabelService.markAttached(id));
    }
}
