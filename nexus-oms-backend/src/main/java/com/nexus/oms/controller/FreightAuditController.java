package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxFreightAuditLog;
import com.nexus.oms.entity.NxFreightInvoice;
import com.nexus.oms.entity.NxFreightInvoiceLine;
import com.nexus.oms.service.FreightAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Freight Audit & Payment", description = "Freight invoice audit, match, approve, dispute, and payment tracking")
@RestController
@RequestMapping("/freight")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FreightAuditController {

    private final FreightAuditService freightAuditService;

    public FreightAuditController(FreightAuditService freightAuditService) {
        this.freightAuditService = freightAuditService;
    }

    // ---- Invoice CRUD ----

    @Operation(summary = "List freight invoices with optional status filter")
    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<NxFreightInvoice>>> getInvoices(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.getInvoices(status)));
    }

    @Operation(summary = "Get a freight invoice by ID")
    @GetMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<NxFreightInvoice>> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.getInvoice(id)));
    }

    @Operation(summary = "Get invoice line items")
    @GetMapping("/invoices/{id}/lines")
    public ResponseEntity<ApiResponse<List<NxFreightInvoiceLine>>> getInvoiceLines(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.getInvoiceLines(id)));
    }

    @Operation(summary = "Create a freight invoice")
    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<NxFreightInvoice>> createInvoice(
            @RequestBody NxFreightInvoice invoice) {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.createInvoice(invoice), "Invoice created"));
    }

    @Operation(summary = "Add a line item to a freight invoice")
    @PostMapping("/invoices/{id}/lines")
    public ResponseEntity<ApiResponse<NxFreightInvoiceLine>> addInvoiceLine(
            @PathVariable UUID id,
            @RequestBody NxFreightInvoiceLine line) {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.addInvoiceLine(id, line), "Line added"));
    }

    // ---- Audit / Match ----

    @Operation(summary = "Run audit match against invoice lines")
    @PostMapping("/invoices/{id}/audit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> performAuditMatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.performAuditMatch(id), "Audit match completed"));
    }

    @Operation(summary = "Get audit log for an invoice")
    @GetMapping("/invoices/{id}/audit-log")
    public ResponseEntity<ApiResponse<List<NxFreightAuditLog>>> getAuditLogs(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.getAuditLogs(id)));
    }

    // ---- State transitions ----

    @Operation(summary = "Approve a freight invoice for payment")
    @PostMapping("/invoices/{id}/approve")
    public ResponseEntity<ApiResponse<NxFreightInvoice>> approveInvoice(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                freightAuditService.approveInvoice(id, approvedBy), "Invoice approved"));
    }

    @Operation(summary = "Dispute a freight invoice")
    @PostMapping("/invoices/{id}/dispute")
    public ResponseEntity<ApiResponse<NxFreightInvoice>> disputeInvoice(
            @PathVariable UUID id,
            @RequestParam String reason,
            @RequestParam(required = false) String performedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                freightAuditService.disputeInvoice(id, reason, performedBy), "Invoice disputed"));
    }

    @Operation(summary = "Mark a freight invoice as paid")
    @PostMapping("/invoices/{id}/pay")
    public ResponseEntity<ApiResponse<NxFreightInvoice>> markPaid(
            @PathVariable UUID id,
            @RequestParam(required = false) String performedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                freightAuditService.markPaid(id, performedBy), "Invoice marked as paid"));
    }

    // ---- Stats ----

    @Operation(summary = "Get freight audit summary statistics")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(freightAuditService.getAuditStats()));
    }
}
