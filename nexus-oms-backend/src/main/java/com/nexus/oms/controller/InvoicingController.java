package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.CreditMemo;
import com.nexus.oms.entity.Invoice;
import com.nexus.oms.entity.InvoiceItem;
import com.nexus.oms.entity.Payment;
import com.nexus.oms.service.InvoicingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/invoicing")
public class InvoicingController {

    private final InvoicingService invoicingService;

    public InvoicingController(InvoicingService invoicingService) {
        this.invoicingService = invoicingService;
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<Page<Invoice>>> getAllInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.getAllInvoices(PageRequest.of(page, size))));
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<Invoice>> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoicingService.getInvoice(id)));
    }

    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<Invoice>> createInvoice(@RequestBody Map<String, Object> request) {
        Invoice invoice = (Invoice) request.get("invoice");
        List<InvoiceItem> items = (List<InvoiceItem>) request.get("items");
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.createInvoice(invoice, items), "Invoice created"));
    }

    @PutMapping("/invoices/{id}/status")
    public ResponseEntity<ApiResponse<Invoice>> updateInvoiceStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.updateInvoiceStatus(id, status), "Invoice status updated"));
    }

    @PostMapping("/invoices/{id}/pay")
    public ResponseEntity<ApiResponse<Payment>> recordPayment(
            @PathVariable UUID id, @RequestBody Payment payment) {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.recordPayment(id, payment), "Payment recorded"));
    }

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<Page<Payment>>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.getAllPayments(PageRequest.of(page, size))));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<ApiResponse<Payment>> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoicingService.getPayment(id)));
    }

    @GetMapping("/invoices/{id}/payments")
    public ResponseEntity<ApiResponse<List<Payment>>> getPaymentsByInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoicingService.getPaymentsByInvoice(id)));
    }

    @PostMapping("/payments/{id}/refund")
    public ResponseEntity<ApiResponse<Payment>> processRefund(
            @PathVariable UUID id, @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.processRefund(id, amount), "Refund processed"));
    }

    @GetMapping("/credit-memos")
    public ResponseEntity<ApiResponse<Page<CreditMemo>>> getAllCreditMemos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.getAllCreditMemos(PageRequest.of(page, size))));
    }

    @GetMapping("/credit-memos/{id}")
    public ResponseEntity<ApiResponse<CreditMemo>> getCreditMemo(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoicingService.getCreditMemo(id)));
    }

    @PostMapping("/credit-memos")
    public ResponseEntity<ApiResponse<CreditMemo>> createCreditMemo(@RequestBody CreditMemo memo) {
        return ResponseEntity.ok(ApiResponse.success(
                invoicingService.createCreditMemo(memo), "Credit memo created"));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInvoiceSummary() {
        return ResponseEntity.ok(ApiResponse.success(invoicingService.getInvoiceSummary()));
    }
}
