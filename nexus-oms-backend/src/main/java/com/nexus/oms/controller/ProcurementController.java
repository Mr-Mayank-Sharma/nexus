package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ProcurementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Procurement", description = "Procurement management APIs")
@RestController
@RequestMapping("/procurement")
public class ProcurementController {

    private final ProcurementService procurementService;

    public ProcurementController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @Operation(summary = "List procurement dashboard")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcurementDashboard() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "ok");
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== SUPPLIERS ====================

    @Operation(summary = "List all suppliers")
    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<Page<Supplier>>> getAllSuppliers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllSuppliers(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get supplier by ID")
    @GetMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<Supplier>> getSupplier(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getSupplier(id)));
    }

    @Operation(summary = "Create a new supplier")
    @PostMapping("/suppliers")
    public ResponseEntity<ApiResponse<Supplier>> createSupplier(@Valid @RequestBody Supplier supplier) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createSupplier(supplier), "Supplier created"));
    }

    @Operation(summary = "Update supplier details")
    @PutMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<Supplier>> updateSupplier(@PathVariable UUID id, @Valid @RequestBody Supplier supplier) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.updateSupplier(id, supplier), "Supplier updated"));
    }

    @Operation(summary = "Delete a supplier")
    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSupplier(@PathVariable UUID id) {
        procurementService.deleteSupplier(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Supplier deleted"));
    }

    @Operation(summary = "Get supplier contacts")
    @GetMapping("/suppliers/{id}/contacts")
    public ResponseEntity<ApiResponse<List<SupplierContact>>> getSupplierContacts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getSupplierContacts(id)));
    }

    @Operation(summary = "Add a supplier contact")
    @PostMapping("/supplier-contacts")
    public ResponseEntity<ApiResponse<SupplierContact>> addSupplierContact(@Valid @RequestBody SupplierContact contact) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addSupplierContact(contact), "Contact added"));
    }

    @Operation(summary = "Get supplier contracts")
    @GetMapping("/suppliers/{id}/contracts")
    public ResponseEntity<ApiResponse<List<SupplierContract>>> getSupplierContracts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getSupplierContracts(id)));
    }

    @Operation(summary = "Add a supplier contract")
    @PostMapping("/supplier-contracts")
    public ResponseEntity<ApiResponse<SupplierContract>> addSupplierContract(@Valid @RequestBody SupplierContract contract) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addSupplierContract(contract), "Contract added"));
    }

    // ==================== PURCHASE REQUESTS ====================

    @Operation(summary = "List all purchase requests")
    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<Page<PurchaseRequest>>> getAllRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllRequests(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get purchase request by ID")
    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<PurchaseRequest>> getRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getRequest(id)));
    }

    @Operation(summary = "Create a purchase request")
    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<PurchaseRequest>> createRequest(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createRequest(request), "Purchase request created"));
    }

    @Operation(summary = "Update purchase request status")
    @PutMapping("/requests/{id}/status")
    public ResponseEntity<ApiResponse<PurchaseRequest>> updateRequestStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "");
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.updateRequestStatus(id, status), "Status updated"));
    }

    @Operation(summary = "Add an item to a purchase request")
    @PostMapping("/requests/{requestId}/items")
    public ResponseEntity<ApiResponse<PurchaseRequestItem>> addRequestItem(
            @PathVariable UUID requestId, @Valid @RequestBody PurchaseRequestItem item) {
        item.setRequestId(requestId);
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addRequestItem(item), "Item added"));
    }

    @Operation(summary = "Submit purchase request for approval")
    @PostMapping("/requests/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseRequest>> submitForApproval(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.submitForApproval(id), "Submitted for approval"));
    }

    @Operation(summary = "Approve a purchase request")
    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseRequest>> approveRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.approveRequest(id), "Request approved"));
    }

    // ==================== RFQs ====================

    @Operation(summary = "List all RFQs")
    @GetMapping("/rfqs")
    public ResponseEntity<ApiResponse<Page<Rfq>>> getAllRfqs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllRfqs(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get RFQ by ID")
    @GetMapping("/rfqs/{id}")
    public ResponseEntity<ApiResponse<Rfq>> getRfq(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getRfq(id)));
    }

    @Operation(summary = "Create a new RFQ")
    @PostMapping("/rfqs")
    public ResponseEntity<ApiResponse<Rfq>> createRfq(@Valid @RequestBody Rfq rfq) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createRfq(rfq), "RFQ created"));
    }

    @Operation(summary = "Submit an RFQ")
    @PostMapping("/rfqs/{id}/submit")
    public ResponseEntity<ApiResponse<Rfq>> submitRfq(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.submitRfq(id), "RFQ submitted"));
    }

    @Operation(summary = "Get RFQ responses")
    @GetMapping("/rfqs/{id}/responses")
    public ResponseEntity<ApiResponse<List<RfqResponse>>> getRfqResponses(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getRfqResponses(id)));
    }

    @Operation(summary = "Add an RFQ response")
    @PostMapping("/rfqs/{rfqId}/responses")
    public ResponseEntity<ApiResponse<RfqResponse>> addRfqResponse(
            @PathVariable UUID rfqId, @Valid @RequestBody RfqResponse response) {
        response.setRfqId(rfqId);
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.addRfqResponse(response), "Response added"));
    }

    // ==================== PURCHASE ORDERS ====================

    @Operation(summary = "List all purchase orders")
    @GetMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<Page<PurchaseOrder>>> getAllPurchaseOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getAllPurchaseOrders(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get purchase order by ID")
    @GetMapping("/purchase-orders/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrder>> getPurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getPurchaseOrder(id)));
    }

    @Operation(summary = "Create a purchase order")
    @PostMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<PurchaseOrder>> createPurchaseOrder(@Valid @RequestBody PurchaseOrder purchaseOrder) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.createPurchaseOrder(purchaseOrder), "Purchase order created"));
    }

    @Operation(summary = "Update purchase order status")
    @PutMapping("/purchase-orders/{id}/status")
    public ResponseEntity<ApiResponse<PurchaseOrder>> updatePurchaseOrderStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "");
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.updatePurchaseOrderStatus(id, status), "Status updated"));
    }

    @Operation(summary = "Receive items for a purchase order")
    @PostMapping("/purchase-orders/{id}/receive")
    public ResponseEntity<ApiResponse<PurchaseOrder>> receiveItems(
            @PathVariable UUID id, @RequestBody List<Map<String, Object>> receivedItems) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.receiveItems(id, receivedItems), "Items received"));
    }

    @Operation(summary = "Approve a purchase order")
    @PostMapping("/purchase-orders/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseOrder>> approvePurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.approvePurchaseOrder(id), "Purchase order approved"));
    }
}
