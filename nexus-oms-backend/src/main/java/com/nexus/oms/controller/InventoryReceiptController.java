package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.InventoryReceiptRequest;
import com.nexus.oms.entity.NxInventoryReceipt;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.InventoryReceiptService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventory-receipts")
public class InventoryReceiptController {

    private final InventoryReceiptService inventoryReceiptService;

    public InventoryReceiptController(InventoryReceiptService inventoryReceiptService) {
        this.inventoryReceiptService = inventoryReceiptService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NxInventoryReceipt>>> getReceipts(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryReceiptService.getReceipts(TenantContext.getCurrentTenantId(), status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxInventoryReceipt>> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inventoryReceiptService.getReceipt(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxInventoryReceipt>> createReceipt(
            @Valid @RequestBody InventoryReceiptRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryReceiptService.createReceipt(TenantContext.getCurrentTenantId(), request),
                "Receipt created"));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<NxInventoryReceipt>> receiveInventory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "system") String receivedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryReceiptService.receiveInventory(id, receivedBy),
                "Inventory received"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReceipt(@PathVariable UUID id) {
        inventoryReceiptService.deleteReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Receipt deleted"));
    }
}
