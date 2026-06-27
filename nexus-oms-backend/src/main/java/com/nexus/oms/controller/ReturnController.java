package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.ReturnResponse;
import com.nexus.oms.entity.NxReturn;
import com.nexus.oms.entity.NxReturnItem;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ReturnService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReturnResponse>>> getReturns(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.getReturns(TenantContext.getCurrentTenantId(), status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReturnResponse>> getReturn(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturn(id)));
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<ApiResponse<List<NxReturnItem>>> getReturnItems(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturnItems(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReturnResponse>> createReturn(@RequestBody CreateReturnRequest request) {
        NxReturn nxReturn = NxReturn.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .orderId(request.orderId)
                .customerId(request.customerId)
                .reason(request.reason)
                .returnChannel(request.returnChannel != null ? request.returnChannel : "MANUAL")
                .rmaType(request.rmaType != null ? request.rmaType : "RETURN")
                .build();
        return ResponseEntity.ok(ApiResponse.success(
                returnService.createReturn(nxReturn, request.items), "Return created"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ReturnResponse>> approveReturn(
            @PathVariable UUID id, @RequestParam(required = false) UUID approvedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.approveReturn(id, approvedBy), "Return approved"));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<ReturnResponse>> receiveReturn(
            @PathVariable UUID id, @RequestParam(required = false) UUID receivedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.receiveReturn(id, receivedBy), "Return received"));
    }

    @PostMapping("/{id}/inspect")
    public ResponseEntity<ApiResponse<ReturnResponse>> inspectReturn(
            @PathVariable UUID id,
            @RequestBody InspectReturnRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.inspectReturn(id, request.items, request.inspectedBy), "Return inspected"));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<ReturnResponse>> processRefund(
            @PathVariable UUID id, @RequestBody RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.processRefund(id, request.refundAmount, request.refundReference), "Refund processed"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ReturnResponse>> rejectReturn(
            @PathVariable UUID id, @RequestBody RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.rejectReturn(id, request.reason), "Return rejected"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ReturnResponse>> updateStatus(
            @PathVariable UUID id, @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.updateReturnStatus(id, request.getStatus()), "Status updated"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.getReturnKPIs(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/reasons")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReturnReasons() {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.getReturnReasons(TenantContext.getCurrentTenantId())));
    }

    public static class CreateReturnRequest {
        public UUID orderId;
        public UUID customerId;
        public String reason;
        public String returnChannel;
        public String rmaType;
        public List<NxReturnItem> items;
    }

    public static class InspectReturnRequest {
        public List<NxReturnItem> items;
        public UUID inspectedBy;
    }

    public static class RefundRequest {
        public BigDecimal refundAmount;
        public String refundReference;
    }

    public static class RejectRequest {
        public String reason;
    }

    public static class UpdateStatusRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
