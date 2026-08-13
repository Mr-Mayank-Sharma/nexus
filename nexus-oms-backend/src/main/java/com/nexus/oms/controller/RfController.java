package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.ScanResult;
import com.nexus.oms.entity.NxCycleCount;
import com.nexus.oms.entity.NxInventoryReceipt;
import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.repository.NxCycleCountRepository;
import com.nexus.oms.repository.NxInventoryReceiptRepository;
import com.nexus.oms.repository.PackageRepository;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.ShipmentRepository;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "RF Scan", description = "Warehouse handheld scan search APIs")
@RestController
@RequestMapping("/rf")
public class RfController {

    private final PicklistRepository picklistRepository;
    private final PackageRepository packageRepository;
    private final ShipmentRepository shipmentRepository;
    private final NxInventoryReceiptRepository receiptRepository;
    private final NxCycleCountRepository cycleCountRepository;

    public RfController(PicklistRepository picklistRepository,
                        PackageRepository packageRepository,
                        ShipmentRepository shipmentRepository,
                        NxInventoryReceiptRepository receiptRepository,
                        NxCycleCountRepository cycleCountRepository) {
        this.picklistRepository = picklistRepository;
        this.packageRepository = packageRepository;
        this.shipmentRepository = shipmentRepository;
        this.receiptRepository = receiptRepository;
        this.cycleCountRepository = cycleCountRepository;
    }

    @Operation(summary = "Search picklists, packages, shipments, receipts and counts by id / SKU / tracking")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ScanResult>>> search(@RequestParam String q) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String needle = q == null ? "" : q.trim();
        List<ScanResult> results = new ArrayList<>();

        if (!needle.isEmpty()) {
            for (NxPicklist pl : picklistRepository.searchByTenantId(tenantId, needle)) {
                results.add(new ScanResult("picklist", pl.getId().toString(), pl.getName(),
                        pl.getPickedItems() + "/" + pl.getTotalItems() + " picked · " + pl.getStatus(),
                        "/rf/pick?id=" + pl.getId()));
            }
            for (NxPackage p : packageRepository.searchByTenantId(tenantId, needle)) {
                results.add(new ScanResult("package", p.getId().toString(),
                        "Package #" + shortId(p.getId()),
                        "Order " + (p.getOrderId() != null ? shortId(p.getOrderId()) : "—") + " · " + p.getStatus(),
                        "/rf/pack?id=" + p.getId()));
            }
            for (NxShipment s : shipmentRepository.searchByTenantId(tenantId, needle)) {
                if (!actionableShipment(s.getStatus())) continue;
                results.add(new ScanResult("shipment", s.getId().toString(),
                        "Shipment #" + shortId(s.getId()),
                        "Order " + (s.getOrderId() != null ? shortId(s.getOrderId()) : "—") + " · " + s.getStatus(),
                        "/rf/ship"));
            }
            for (NxInventoryReceipt r : receiptRepository.searchByTenantId(tenantId, needle)) {
                if (!actionableReceipt(r)) continue;
                results.add(new ScanResult("receipt", r.getId().toString(),
                        r.getProductName() != null && !r.getProductName().isBlank() ? r.getProductName() : r.getSku(),
                        "SKU " + r.getSku() + " · " + (r.getReceiptType() != null ? r.getReceiptType() : "RECEIPT"),
                        "/rf/receive"));
            }
            for (NxCycleCount c : cycleCountRepository.searchByTenantId(tenantId, needle)) {
                if (!actionableCount(c)) continue;
                results.add(new ScanResult("count", c.getId().toString(),
                        c.getProductName() != null && !c.getProductName().isBlank() ? c.getProductName() : c.getSku(),
                        "SKU " + c.getSku() + " · expected " + c.getExpectedQty(),
                        "/rf/count"));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(results));
    }

    private static boolean actionableShipment(String status) {
        if (status == null) return true;
        return !List.of("SHIPPED", "DELIVERED", "COMPLETED").contains(status.toUpperCase());
    }

    private static boolean actionableReceipt(NxInventoryReceipt r) {
        if (r.getReceivedAt() != null) return false;
        return r.getStatus() == null || !List.of("RECEIVED", "COMPLETED").contains(r.getStatus().toUpperCase());
    }

    private static boolean actionableCount(NxCycleCount c) {
        if (c.getCountedAt() != null) return false;
        return c.getStatus() == null || !List.of("COUNTED", "COMPLETED").contains(c.getStatus().toUpperCase());
    }

    private static String shortId(UUID id) {
        return id != null ? id.toString().substring(0, 8) : "";
    }
}
