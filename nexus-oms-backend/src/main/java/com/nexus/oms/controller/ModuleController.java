package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.CreditMemo;
import com.nexus.oms.entity.Invoice;
import com.nexus.oms.entity.Payment;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.InvoicingService;
import com.nexus.oms.service.WarehouseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ModuleController {

    private final InvoicingService invoicingService;
    private final WarehouseService warehouseService;

    public ModuleController(InvoicingService invoicingService, WarehouseService warehouseService) {
        this.invoicingService = invoicingService;
        this.warehouseService = warehouseService;
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<Page<Invoice>>> getInvoices() {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.getAllInvoices(PageRequest.of(0, 20))));
    }

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<Page<Payment>>> getPayments() {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.getAllPayments(PageRequest.of(0, 20))));
    }

    @GetMapping("/warehouse")
    public ResponseEntity<ApiResponse<Page<Warehouse>>> getWarehouses() {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseService.getAllWarehouses(PageRequest.of(0, 20))));
    }

    @GetMapping("/routing")
    public ResponseEntity<ApiResponse<Map<String, String>>> getRouting() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAudit() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @GetMapping("/integration")
    public ResponseEntity<ApiResponse<Map<String, String>>> getIntegration() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @GetMapping("/fulfillment")
    public ResponseEntity<ApiResponse<Map<String, String>>> getFulfillment() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @GetMapping("/ai")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAi() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }
}
