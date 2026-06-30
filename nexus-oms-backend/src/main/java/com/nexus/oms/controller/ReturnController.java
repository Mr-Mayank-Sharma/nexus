package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.ReturnResponse;
import com.nexus.oms.entity.NxReturn;
import com.nexus.oms.entity.NxReturnItem;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ReturnService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Returns", description = "Return management APIs")
@RestController
@RequestMapping("/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @Operation(summary = "List returns with optional status filter")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReturnResponse>>> getReturns(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.getReturns(TenantContext.getCurrentTenantId(), status)));
    }

    @Operation(summary = "Get return by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReturnResponse>> getReturn(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturn(id)));
    }

    @Operation(summary = "Get items for a return")
    @GetMapping("/{id}/items")
    public ResponseEntity<ApiResponse<List<NxReturnItem>>> getReturnItems(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturnItems(id)));
    }

    @Operation(summary = "Create a new return")
    @PostMapping
    public ResponseEntity<ApiResponse<ReturnResponse>> createReturn(@Valid @RequestBody CreateReturnRequest request) {
        NxReturn nxReturn = NxReturn.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .orderId(request.orderId)
                .customerId(request.customerId)
                .reason(request.reason)
                .build();
        return ResponseEntity.ok(ApiResponse.success(
                returnService.createReturn(nxReturn, request.items), "Return created"));
    }

    @Operation(summary = "Approve a return request")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ReturnResponse>> approveReturn(
            @PathVariable UUID id, @RequestParam(required = false) UUID approvedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.approveReturn(id, approvedBy), "Return approved"));
    }

    @Operation(summary = "Mark return as received")
    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<ReturnResponse>> receiveReturn(
            @PathVariable UUID id, @RequestParam(required = false) UUID receivedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.receiveReturn(id, receivedBy), "Return received"));
    }

    @Operation(summary = "Inspect returned items and set condition/disposition")
    @PostMapping("/{id}/inspect")
    public ResponseEntity<ApiResponse<ReturnResponse>> inspectReturn(
            @PathVariable UUID id,
            @Valid @RequestBody InspectReturnRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.inspectReturn(id, request.items, request.inspectedBy), "Return inspected"));
    }

    @Operation(summary = "Process refund for a return")
    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<ReturnResponse>> processRefund(
            @PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.processRefund(id, request.refundAmount, request.refundReference), "Refund processed"));
    }

    @Operation(summary = "Reject a return")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ReturnResponse>> rejectReturn(
            @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.rejectReturn(id, request.reason), "Return rejected"));
    }

    @Operation(summary = "Update return status")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ReturnResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.updateReturnStatus(id, request.getStatus()), "Status updated"));
    }

    @Operation(summary = "Get return KPIs")
    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.getReturnKPIs(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Get return reasons")
    @GetMapping("/reasons")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReturnReasons() {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.getReturnReasons(TenantContext.getCurrentTenantId())));
    }

    public static class CreateReturnRequest {
        @NotNull
        public UUID orderId;
        @NotNull
        public UUID customerId;
        @NotBlank
        public String reason;
        public String returnChannel;
        public String rmaType;
        @NotEmpty
        public List<NxReturnItem> items;
    }

    public static class InspectReturnRequest {
        @NotEmpty
        public List<NxReturnItem> items;
        @NotNull
        public UUID inspectedBy;
    }

    public static class RefundRequest {
        @NotNull @Positive
        public BigDecimal refundAmount;
        @NotBlank
        public String refundReference;
    }

    public static class RejectRequest {
        @NotBlank
        public String reason;
    }

    public static class UpdateStatusRequest {
        @NotBlank
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
