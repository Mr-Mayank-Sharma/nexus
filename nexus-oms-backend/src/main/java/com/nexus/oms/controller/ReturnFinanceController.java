package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.CreditMemoDetail;
import com.nexus.oms.dto.LesserValueExchangeRequest;
import com.nexus.oms.dto.SplitCreditMemoRequest;
import com.nexus.oms.entity.CreditMemo;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ReturnFinanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/returns-finance")
public class ReturnFinanceController {

    private final ReturnFinanceService returnFinanceService;

    public ReturnFinanceController(ReturnFinanceService returnFinanceService) {
        this.returnFinanceService = returnFinanceService;
    }

    @PostMapping("/credit-memos/split")
    public ResponseEntity<ApiResponse<List<CreditMemo>>> splitCreditMemos(
            @RequestBody SplitCreditMemoRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnFinanceService.splitCreditMemos(request),
                "Credit memos split"));
    }

    @PostMapping("/exchange/lesser-value")
    public ResponseEntity<ApiResponse<CreditMemo>> lesserValueExchange(
            @RequestBody LesserValueExchangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnFinanceService.lesserValueExchange(request),
                "Lesser-value exchange processed"));
    }

    @GetMapping("/returns/{returnId}/credit-memos")
    public ResponseEntity<ApiResponse<List<CreditMemoDetail>>> getCreditMemosForReturn(
            @PathVariable UUID returnId) {
        return ResponseEntity.ok(ApiResponse.success(
                returnFinanceService.getCreditMemosForReturn(returnId)));
    }

    @PostMapping("/credit-memos/{id}/deposit")
    public ResponseEntity<ApiResponse<CreditMemo>> applyCustomerDeposit(
            @PathVariable UUID id,
            @RequestParam BigDecimal deposit) {
        return ResponseEntity.ok(ApiResponse.success(
                returnFinanceService.applyCustomerDeposit(id, deposit),
                "Customer deposit applied"));
    }

    @GetMapping("/impact")
    public ResponseEntity<ApiResponse<ReturnFinanceService.ImpactReport>> getImpact() {
        return ResponseEntity.ok(ApiResponse.success(
                returnFinanceService.getSingleMemoImpact(TenantContext.getCurrentTenantId())));
    }
}
