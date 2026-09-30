package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.reconciliation.ReconciliationReportRow;
import com.nexus.oms.entity.NxFulfillmentReconciliation;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.FulfillmentReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * T-09: Shopify → OMS fulfillment reconciliation endpoints.
 *
 * Exposes the reconciliation ledger: mismatches requiring review, per-order
 * fulfillment history, and an on-demand cross-system repair trigger.
 */
@RestController
@RequestMapping("/reconciliation")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FulfillmentReconciliationController {

    private final FulfillmentReconciliationService reconciliationService;

    public FulfillmentReconciliationController(FulfillmentReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** Ledger-based mismatch report (FLAGGED / REPAIRED rows). */
    @GetMapping("/mismatches")
    public ResponseEntity<ApiResponse<List<ReconciliationReportRow>>> getMismatches() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.getMismatchReport(tenantId)));
    }

    /** Full reconciliation history for a single order. */
    @GetMapping("/orders/{orderId}/history")
    public ResponseEntity<ApiResponse<List<NxFulfillmentReconciliation>>> getOrderHistory(@PathVariable UUID orderId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.getOrderHistory(tenantId, orderId)));
    }

    /** On-demand cross-system repair pass. */
    @PostMapping("/repair")
    public ResponseEntity<ApiResponse<String>> triggerRepair() {
        reconciliationService.triggerRepair();
        return ResponseEntity.ok(ApiResponse.success("Repair pass completed", "Cross-system reconciliation repair triggered"));
    }
}