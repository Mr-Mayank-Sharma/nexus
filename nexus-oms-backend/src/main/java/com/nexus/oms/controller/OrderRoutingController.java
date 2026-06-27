package com.nexus.oms.controller;

import com.nexus.oms.dto.AllocationRequest;
import com.nexus.oms.dto.AllocationResult;
import com.nexus.oms.dto.ExceptionResolutionRequest;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxOrderAllocation;
import com.nexus.oms.entity.NxFulfillmentException;
import com.nexus.oms.service.OrderRoutingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/order-routing")
public class OrderRoutingController {

    private final OrderRoutingService orderRoutingService;

    public OrderRoutingController(OrderRoutingService orderRoutingService) {
        this.orderRoutingService = orderRoutingService;
    }

    @PostMapping("/allocate")
    public ResponseEntity<ApiResponse<AllocationResult>> allocateOrder(
            @Valid @RequestBody AllocationRequest request) {
        AllocationResult result = orderRoutingService.allocateOrder(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Order allocated successfully"));
    }

    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateAllocation(
            @Valid @RequestBody AllocationRequest request) {
        Map<String, Object> result = orderRoutingService.simulateAllocation(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Allocation simulation completed"));
    }

    @PostMapping("/reallocate")
    public ResponseEntity<ApiResponse<AllocationResult>> reallocateOrder(
            @RequestParam UUID orderId,
            @RequestParam(defaultValue = "HYBRID") String strategy) {
        AllocationResult result = orderRoutingService.reallocateOrder(orderId, strategy);
        return ResponseEntity.ok(ApiResponse.success(result, "Order reallocated successfully"));
    }

    @GetMapping("/allocations/{orderId}")
    public ResponseEntity<ApiResponse<List<NxOrderAllocation>>> getAllocations(
            @PathVariable UUID orderId) {
        List<NxOrderAllocation> allocations = orderRoutingService.getAllocationsForOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(allocations));
    }

    @GetMapping("/exceptions")
    public ResponseEntity<ApiResponse<Page<NxFulfillmentException>>> getExceptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NxFulfillmentException> exceptions = orderRoutingService.getExceptions(status, severity, pageable);
        return ResponseEntity.ok(ApiResponse.success(exceptions));
    }

    @GetMapping("/exceptions/{id}")
    public ResponseEntity<ApiResponse<NxFulfillmentException>> getException(
            @PathVariable UUID id) {
        NxFulfillmentException exception = orderRoutingService.getException(id);
        return ResponseEntity.ok(ApiResponse.success(exception));
    }

    @PostMapping("/exceptions/{id}/resolve")
    public ResponseEntity<ApiResponse<NxFulfillmentException>> resolveException(
            @PathVariable UUID id,
            @Valid @RequestBody ExceptionResolutionRequest request) {
        NxFulfillmentException exception = orderRoutingService.resolveException(id, request);
        return ResponseEntity.ok(ApiResponse.success(exception, "Exception resolved"));
    }

    @PostMapping("/exceptions/{id}/escalate")
    public ResponseEntity<ApiResponse<NxFulfillmentException>> escalateException(
            @PathVariable UUID id) {
        NxFulfillmentException exception = orderRoutingService.escalateException(id);
        return ResponseEntity.ok(ApiResponse.success(exception, "Exception escalated"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        Map<String, Object> kpis = orderRoutingService.getRoutingKPIs();
        return ResponseEntity.ok(ApiResponse.success(kpis));
    }
}
