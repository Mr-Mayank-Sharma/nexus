package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ProcurementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/procurement")
public class ProcurementController {

    private final ProcurementService procurementService;

    public ProcurementController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    // ==================== SUPPLIERS ====================

    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<Page<Supplier>>> getAllSuppliers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllSuppliers(PageRequest.of(page, size))));
    }

    @GetMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<Supplier>> getSupplier(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getSupplier(id)));
    }

    @PostMapping("/suppliers")
    public ResponseEntity<ApiResponse<Supplier>> createSupplier(@RequestBody Supplier supplier) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createSupplier(supplier), "Supplier created"));
    }

    @PutMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<Supplier>> updateSupplier(@PathVariable UUID id, @RequestBody Supplier supplier) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.updateSupplier(id, supplier), "Supplier updated"));
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSupplier(@PathVariable UUID id) {
        procurementService.deleteSupplier(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Supplier deleted"));
    }

    @GetMapping("/suppliers/{id}/contacts")
    public ResponseEntity<ApiResponse<List<SupplierContact>>> getSupplierContacts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getSupplierContacts(id)));
    }

    @PostMapping("/suppliers/contacts")
    public ResponseEntity<ApiResponse<SupplierContact>> addSupplierContact(@RequestBody SupplierContact contact) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addSupplierContact(contact), "Contact added"));
    }

    @GetMapping("/suppliers/{id}/contracts")
    public ResponseEntity<ApiResponse<List<SupplierContract>>> getSupplierContracts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getSupplierContracts(id)));
    }

    @PostMapping("/suppliers/contracts")
    public ResponseEntity<ApiResponse<SupplierContract>> addSupplierContract(@RequestBody SupplierContract contract) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addSupplierContract(contract), "Contract added"));
    }

    // ==================== PURCHASE REQUESTS ====================

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<Page<PurchaseRequest>>> getAllRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllRequests(PageRequest.of(page, size))));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<PurchaseRequest>> getRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getRequest(id)));
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<PurchaseRequest>> createRequest(@RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createRequest(request), "Purchase request created"));
    }

    @PutMapping("/requests/{id}/status")
    public ResponseEntity<ApiResponse<PurchaseRequest>> updateRequestStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.updateRequestStatus(id, status), "Status updated"));
    }

    @PostMapping("/requests/items")
    public ResponseEntity<ApiResponse<PurchaseRequestItem>> addRequestItem(@RequestBody PurchaseRequestItem item) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addRequestItem(item), "Item added"));
    }

    @PutMapping("/requests/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseRequest>> submitForApproval(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.submitForApproval(id), "Submitted for approval"));
    }

    @PutMapping("/requests/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseRequest>> approveRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.approveRequest(id), "Request approved"));
    }

    // ==================== RFQs ====================

    @GetMapping("/rfqs")
    public ResponseEntity<ApiResponse<Page<Rfq>>> getAllRfqs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllRfqs(PageRequest.of(page, size))));
    }

    @GetMapping("/rfqs/{id}")
    public ResponseEntity<ApiResponse<Rfq>> getRfq(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getRfq(id)));
    }

    @PostMapping("/rfqs")
    public ResponseEntity<ApiResponse<Rfq>> createRfq(@RequestBody Rfq rfq) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createRfq(rfq), "RFQ created"));
    }

    @PutMapping("/rfqs/{id}/submit")
    public ResponseEntity<ApiResponse<Rfq>> submitRfq(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.submitRfq(id), "RFQ submitted"));
    }

    @GetMapping("/rfqs/{id}/responses")
    public ResponseEntity<ApiResponse<List<RfqResponse>>> getRfqResponses(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getRfqResponses(id)));
    }

    @PostMapping("/rfqs/responses")
    public ResponseEntity<ApiResponse<RfqResponse>> addRfqResponse(@RequestBody RfqResponse response) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addRfqResponse(response), "Response added"));
    }

    // ==================== PURCHASE ORDERS ====================

    @GetMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<Page<PurchaseOrder>>> getAllPurchaseOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllPurchaseOrders(PageRequest.of(page, size))));
    }

    @GetMapping("/purchase-orders/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrder>> getPurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getPurchaseOrder(id)));
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<PurchaseOrder>> createPurchaseOrder(@RequestBody PurchaseOrder purchaseOrder) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createPurchaseOrder(purchaseOrder), "Purchase order created"));
    }

    @PutMapping("/purchase-orders/{id}/status")
    public ResponseEntity<ApiResponse<PurchaseOrder>> updatePurchaseOrderStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.updatePurchaseOrderStatus(id, status), "Status updated"));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public ResponseEntity<ApiResponse<PurchaseOrder>> receiveItems(
            @PathVariable UUID id, @RequestBody List<Map<String, Object>> receivedItems) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.receiveItems(id, receivedItems), "Items received"));
    }

    @PutMapping("/purchase-orders/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseOrder>> approvePurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.approvePurchaseOrder(id), "Purchase order approved"));
    }
}
