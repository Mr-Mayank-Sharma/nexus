package com.nexus.oms.controller;

import com.nexus.oms.dto.*;
import com.nexus.oms.dto.ai.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.OrderService;
import com.nexus.oms.service.ai.AiOrderActionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Orders", description = "Order management APIs")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final AiOrderActionService aiOrderActionService;

    public OrderController(OrderService orderService, AiOrderActionService aiOrderActionService) {
        this.orderService = orderService;
        this.aiOrderActionService = aiOrderActionService;
    }

    @Operation(summary = "Create a new order")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.createOrder(TenantContext.getCurrentTenantId(), request), "Order created successfully"));
    }

    @Operation(summary = "List all orders with pagination")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrders(TenantContext.getCurrentTenantId(), status, search, pageable)));
    }

    @Operation(summary = "Get order by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(id)));
    }

    @Operation(summary = "Update order status")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.updateStatus(id, request.getStatus(), request.getSubStatus(),
                        request.getTrackingNumber(), request.getCarrierId())));
    }

    @Operation(summary = "Confirm an order")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.confirmOrder(id), "Order confirmed"));
    }

    @Operation(summary = "Allocate inventory for an order")
    @PostMapping("/{id}/allocate")
    public ResponseEntity<ApiResponse<AllocationResponse>> allocateOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.allocateOrder(id), "Order allocated"));
    }

    @Operation(summary = "Ship an order")
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<OrderResponse>> shipOrder(
            @PathVariable UUID id,
            @RequestParam String carrierId,
            @RequestParam String trackingNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.shipOrder(id, carrierId, trackingNumber), "Order shipped"));
    }

    @Operation(summary = "Cancel an order")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.cancelOrder(id), "Order cancelled"));
    }

    @Operation(summary = "Modify existing order")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> modifyOrder(
            @PathVariable UUID id,
            @Valid @RequestBody OrderModifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.modifyOrder(id, TenantContext.getCurrentTenantId(), request), "Order modified"));
    }

    @Operation(summary = "Split an order into multiple orders")
    @PostMapping("/{id}/split")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> splitOrder(
            @PathVariable UUID id,
            @Valid @RequestBody SplitOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.splitOrder(id, TenantContext.getCurrentTenantId(), request), "Order split"));
    }

    @Operation(summary = "Merge multiple orders into one")
    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<OrderResponse>> mergeOrders(
            @Valid @RequestBody MergeOrdersRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.mergeOrders(request, TenantContext.getCurrentTenantId()), "Orders merged"));
    }

    @Operation(summary = "Get AI suggestions for an order")
    @GetMapping("/{id}/ai-suggestions")
    public ResponseEntity<ApiResponse<List<AiSuggestionDto>>> getAiSuggestions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                aiOrderActionService.getSuggestions(id)));
    }

    @Operation(summary = "Get AI action history for an order")
    @GetMapping("/{id}/ai-history")
    public ResponseEntity<ApiResponse<List<AiActionHistoryDto>>> getAiHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                aiOrderActionService.getActionHistory(id)));
    }

    @Operation(summary = "Execute an AI-powered action on an order")
    @PostMapping("/{id}/ai-execute")
    public ResponseEntity<ApiResponse<AiActionHistoryDto>> executeAiAction(
            @PathVariable UUID id,
            @Valid @RequestBody AiExecuteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                aiOrderActionService.executeAction(id, request, TenantContext.getCurrentTenantId()), "AI action executed"));
    }
}
