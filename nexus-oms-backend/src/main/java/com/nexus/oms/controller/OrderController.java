package com.nexus.oms.controller;

import com.nexus.oms.dto.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.createOrder(TenantContext.getCurrentTenantId(), request), "Order created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrders(TenantContext.getCurrentTenantId(), status, search, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(id)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.updateStatus(id, request.getStatus(), request.getSubStatus(),
                        request.getTrackingNumber(), request.getCarrierId())));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.confirmOrder(id), "Order confirmed"));
    }

    @PostMapping("/{id}/allocate")
    public ResponseEntity<ApiResponse<AllocationResponse>> allocateOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.allocateOrder(id), "Order allocated"));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<OrderResponse>> shipOrder(
            @PathVariable UUID id,
            @RequestParam String carrierId,
            @RequestParam String trackingNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.shipOrder(id, carrierId, trackingNumber), "Order shipped"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.cancelOrder(id), "Order cancelled"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> modifyOrder(
            @PathVariable UUID id,
            @Valid @RequestBody OrderModifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.modifyOrder(id, TenantContext.getCurrentTenantId(), request), "Order modified"));
    }

    @PostMapping("/{id}/split")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> splitOrder(
            @PathVariable UUID id,
            @Valid @RequestBody SplitOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.splitOrder(id, TenantContext.getCurrentTenantId(), request), "Order split"));
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<OrderResponse>> mergeOrders(
            @Valid @RequestBody MergeOrdersRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.mergeOrders(request, TenantContext.getCurrentTenantId()), "Orders merged"));
    }
}
