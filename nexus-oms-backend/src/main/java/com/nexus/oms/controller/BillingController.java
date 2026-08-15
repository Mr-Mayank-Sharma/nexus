package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxBillingStatement;
import com.nexus.oms.entity.NxBillingStatementLine;
import com.nexus.oms.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "3PL Billing", description = "Multi-client billing statement generation and management")
@RestController
@RequestMapping("/billing/statements")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @Operation(summary = "Generate a billing statement from real order data and the client's rate card")
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<NxBillingStatement>> generate(
            @RequestParam UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.generateStatement(clientId, periodStart, periodEnd), "Statement generated"));
    }

    @Operation(summary = "List statements (optionally filtered by client)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxBillingStatement>>> list(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getStatements(clientId)));
    }

    @Operation(summary = "Get a statement with its line items")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID id) {
        NxBillingStatement statement = billingService.getStatement(id);
        List<NxBillingStatementLine> lines = billingService.getStatementLines(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("statement", statement, "lines", lines)));
    }

    @Operation(summary = "Change statement status (ISSUED/PAID)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<NxBillingStatement>> updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(billingService.updateStatus(id, status), "Statement status updated"));
    }
}
