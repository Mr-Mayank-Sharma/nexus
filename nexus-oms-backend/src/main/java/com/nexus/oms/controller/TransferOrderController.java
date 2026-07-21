package com.nexus.oms.controller;

import com.nexus.oms.dto.TransferOrderRequest;
import com.nexus.oms.entity.NxTransferOrder;
import com.nexus.oms.entity.NxTransferOrderItem;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.TransferOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TransferOrderController {

    private final TransferOrderService transferOrderService;

    public TransferOrderController(TransferOrderService transferOrderService) {
        this.transferOrderService = transferOrderService;
    }

    @PostMapping
    public ResponseEntity<NxTransferOrder> createTransferOrder(@RequestBody TransferOrderRequest request) {
        NxTransferOrder transferOrder = transferOrderService.createTransferOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transferOrder);
    }

    @GetMapping
    public ResponseEntity<List<NxTransferOrder>> getTransferOrders(
            @RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxTransferOrder> transfers = transferOrderService.getTransferOrders(tenantId, status);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NxTransferOrder> getTransferOrder(@PathVariable UUID id) {
        NxTransferOrder transferOrder = transferOrderService.getTransferOrder(id);
        return ResponseEntity.ok(transferOrder);
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<List<NxTransferOrderItem>> getTransferOrderItems(@PathVariable UUID id) {
        List<NxTransferOrderItem> items = transferOrderService.getTransferOrderItems(id);
        return ResponseEntity.ok(items);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<NxTransferOrder> approveTransferOrder(@PathVariable UUID id) {
        UUID approvedBy = TenantContext.getCurrentUserId();
        NxTransferOrder transferOrder = transferOrderService.approveTransferOrder(id, approvedBy);
        return ResponseEntity.ok(transferOrder);
    }

    @PutMapping("/{id}/ship")
    public ResponseEntity<NxTransferOrder> shipTransferOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) List<NxTransferOrderItem> shippedItems) {
        UUID shippedBy = TenantContext.getCurrentUserId();
        NxTransferOrder transferOrder = transferOrderService.shipTransferOrder(id, shippedBy, shippedItems);
        return ResponseEntity.ok(transferOrder);
    }

    @PutMapping("/{id}/receive")
    public ResponseEntity<NxTransferOrder> receiveTransferOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) List<NxTransferOrderItem> receivedItems) {
        UUID receivedBy = TenantContext.getCurrentUserId();
        NxTransferOrder transferOrder = transferOrderService.receiveTransferOrder(id, receivedBy, receivedItems);
        return ResponseEntity.ok(transferOrder);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<NxTransferOrder> cancelTransferOrder(@PathVariable UUID id) {
        UUID cancelledBy = TenantContext.getCurrentUserId();
        NxTransferOrder transferOrder = transferOrderService.cancelTransferOrder(id, cancelledBy);
        return ResponseEntity.ok(transferOrder);
    }

    @GetMapping("/in-transit")
    public ResponseEntity<List<NxTransferOrder>> getTransfersInTransit() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxTransferOrder> transfers = transferOrderService.getTransfersInTransit(tenantId);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/node/{nodeId}")
    public ResponseEntity<List<NxTransferOrder>> getTransfersByNode(@PathVariable UUID nodeId) {
        List<NxTransferOrder> transfers = transferOrderService.getTransfersByNode(nodeId);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTransferStats() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> stats = transferOrderService.getTransferStats(tenantId);
        return ResponseEntity.ok(stats);
    }
}
