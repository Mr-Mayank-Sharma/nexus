package com.nexus.oms.coreapi;

import com.nexus.core.model.inventory.InventoryTransfer;
import com.nexus.core.service.StoreNetworkService;
import com.nexus.core.service.TransferService;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The store network on the core model (doc 24, section 4b): transfers from request to receipt, stock lookup across
 * stores with confidence, holds for pickup, sales credit and rebalancing suggestions. Admin only ({@code /core/}).
 */
@RestController
@RequestMapping("/core/network")
@Tag(name = "Core store network", description = "Transfers, lookup with confidence, pickup holds, sales credit, rebalancing")
public class CoreNetworkController {

    private final TransferService transfers;
    private final StoreNetworkService network;
    private final EntityManager em;

    public CoreNetworkController(TransferService transfers, StoreNetworkService network, EntityManager em) {
        this.transfers = transfers;
        this.network = network;
        this.em = em;
    }

    public record Location(String facilityId, String facilityName, String facilityTypeId) {
    }

    public record ProductRef(String productId, String productName, String sku) {
    }

    @Operation(summary = "Locations that can hold stock (not the virtual ones)")
    @GetMapping("/locations")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Location>>> locations() {
        List<Location> out = em.createQuery("SELECT f.facilityId, f.facilityName, f.facilityTypeId FROM Facility f WHERE f.facilityTypeId NOT IN "
                + "('VIRTUAL_FACILITY', 'IN_TRANSIT', 'DROPSHIP') ORDER BY f.facilityName", Object[].class).getResultList().stream()
                .map(r -> new Location((String) r[0], (String) r[1], (String) r[2])).toList();
        return ResponseEntity.ok(ApiResponse.success(out, "Locations"));
    }

    @Operation(summary = "Products with stock at a location, for picking what to transfer")
    @GetMapping("/locations/{facilityId}/products")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> productsAt(@PathVariable String facilityId, @RequestParam(defaultValue = "") String q) {
        String like = "%" + q.trim().toLowerCase() + "%";
        List<Map<String, Object>> out = new ArrayList<>();
        em.createQuery("SELECT p.productId, p.productName, g.idValue, pf.computedLastInventoryCount, pf.lastInventoryCount FROM ProductFacility pf, Product p, "
                        + "GoodIdentification g WHERE p.productId = pf.productId AND g.productId = p.productId AND g.goodIdentificationTypeId = 'SKU' "
                        + "AND g.thruDate IS NULL AND pf.facilityId = :f AND pf.lastInventoryCount > 0 AND (LOWER(p.productName) LIKE :q OR LOWER(g.idValue) LIKE :q) "
                        + "ORDER BY p.productName", Object[].class)
                .setParameter("f", facilityId).setParameter("q", like).setMaxResults(50).getResultList()
                .forEach(r -> out.add(Map.of("productId", r[0], "productName", r[1] == null ? "" : r[1], "sku", r[2], "sellable", r[3], "available", r[4])));
        return ResponseEntity.ok(ApiResponse.success(out, "Products"));
    }

    @Operation(summary = "Find products by name or SKU")
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<ProductRef>>> products(@RequestParam String q) {
        String like = "%" + q.trim().toLowerCase() + "%";
        List<ProductRef> out = em.createQuery("SELECT p.productId, p.productName, g.idValue FROM Product p, GoodIdentification g WHERE g.productId = p.productId "
                        + "AND g.goodIdentificationTypeId = 'SKU' AND g.thruDate IS NULL AND (LOWER(p.productName) LIKE :q OR LOWER(g.idValue) LIKE :q) "
                        + "ORDER BY p.productName", Object[].class).setParameter("q", like).setMaxResults(20).getResultList().stream()
                .map(r -> new ProductRef((String) r[0], (String) r[1], (String) r[2])).toList();
        return ResponseEntity.ok(ApiResponse.success(out, "Products"));
    }

    // ── transfers ──

    public record NewTransfer(String fromFacilityId, String toFacilityId, List<TransferService.Line> lines, String comments) {
    }

    public record Line(String inventoryTransferId, String productId, String productName, BigDecimal quantity, String statusId, String comments) {
    }

    public record Transfer(String shipmentId, String statusId, String fromFacilityId, String toFacilityId, String createdDate, List<Line> lines) {
    }

    @Operation(summary = "Transfers, newest first")
    @GetMapping("/transfers")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Transfer>>> list() {
        List<Object[]> rows = em.createQuery("SELECT s.shipmentId, s.statusId, s.originFacilityId, s.destinationFacilityId, s.createdDate FROM Shipment s "
                + "WHERE s.shipmentTypeId = 'TRANSFER' ORDER BY s.createdDate DESC", Object[].class).setMaxResults(100).getResultList();
        List<Transfer> out = rows.stream().map(r -> new Transfer((String) r[0], (String) r[1], (String) r[2], (String) r[3], String.valueOf(r[4]), lines((String) r[0]))).toList();
        return ResponseEntity.ok(ApiResponse.success(out, "Transfers"));
    }

    @Operation(summary = "Request a transfer")
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<String>> request(@RequestBody NewTransfer t) {
        return ResponseEntity.ok(ApiResponse.success(transfers.request(t.fromFacilityId(), t.toFacilityId(), t.lines(), t.comments(), user()), "Transfer requested"));
    }

    @Operation(summary = "Approve: the stock is held at the sending location")
    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable String id) {
        transfers.approve(id, user());
        return ResponseEntity.ok(ApiResponse.success(null, "Approved; stock held at the sending location"));
    }

    @Operation(summary = "Ship: the stock leaves the sending location and is in transit")
    @PostMapping("/transfers/{id}/ship")
    public ResponseEntity<ApiResponse<Void>> ship(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        transfers.ship(id, body == null ? null : body.get("trackingNumber"), user());
        return ResponseEntity.ok(ApiResponse.success(null, "Shipped; in transit"));
    }

    @Operation(summary = "Receive by scan: quantity received per product")
    @PostMapping("/transfers/{id}/receive")
    public ResponseEntity<ApiResponse<List<TransferService.Receipt>>> receive(@PathVariable String id, @RequestBody Map<String, BigDecimal> scanned) {
        return ResponseEntity.ok(ApiResponse.success(transfers.receive(id, scanned, user()), "Received"));
    }

    public record Settle(String productId, BigDecimal found, String note) {
    }

    @Operation(summary = "Settle a difference: found units go on sale, the rest is written off as lost in transit")
    @PostMapping("/transfers/{id}/settle")
    public ResponseEntity<ApiResponse<Void>> settle(@PathVariable String id, @RequestBody Settle s) {
        transfers.settle(id, s.productId(), s.found(), s.note(), user());
        return ResponseEntity.ok(ApiResponse.success(null, "Settled"));
    }

    @Operation(summary = "Cancel a transfer not yet shipped")
    @PostMapping("/transfers/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable String id) {
        transfers.cancel(id, user());
        return ResponseEntity.ok(ApiResponse.success(null, "Cancelled"));
    }

    // ── lookup, holds, credit, rebalancing ──

    @Operation(summary = "Which stores can sell a product now, with a confidence level and its reasons")
    @GetMapping("/lookup/{productId}")
    public ResponseEntity<ApiResponse<List<StoreNetworkService.Availability>>> lookup(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(network.lookup(productId), "Availability"));
    }

    public record Hold(String productStoreId, String productId, String facilityId, BigDecimal quantity, String customerPartyId, long holdHours,
                       String sellingFacilityId) {
    }

    @Operation(summary = "Hold a product at a store for a customer to collect")
    @PostMapping("/holds")
    public ResponseEntity<ApiResponse<String>> hold(@RequestBody Hold h) {
        return ResponseEntity.ok(ApiResponse.success(network.holdForPickup(h.productStoreId(), h.productId(), h.facilityId(), h.quantity(), h.customerPartyId(),
                h.holdHours(), h.sellingFacilityId(), user()), "Held for pickup"));
    }

    @Operation(summary = "Sales credit of an order between the selling and the fulfilling store")
    @GetMapping("/orders/{orderId}/sales-credit")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> credit(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(network.salesCredit(orderId), "Sales credit"));
    }

    @Operation(summary = "Transfers worth making inside a store cluster before stock is marked down")
    @GetMapping("/clusters/{facilityGroupId}/rebalance")
    public ResponseEntity<ApiResponse<List<StoreNetworkService.Suggestion>>> rebalance(@PathVariable String facilityGroupId,
                                                                                      @RequestParam(defaultValue = "2") double lowWeeks,
                                                                                      @RequestParam(defaultValue = "12") double highWeeks,
                                                                                      @RequestParam(defaultValue = "4") double targetWeeks) {
        return ResponseEntity.ok(ApiResponse.success(network.rebalance(facilityGroupId, lowWeeks, highWeeks, targetWeeks), "Suggestions"));
    }

    private List<Line> lines(String shipmentId) {
        List<Line> out = new ArrayList<>();
        for (InventoryTransfer t : transfers.lines(shipmentId)) {
            Object[] p = em.createQuery("SELECT p.productId, p.productName FROM InventoryItem i, Product p WHERE p.productId = i.productId AND i.inventoryItemId = :i",
                    Object[].class).setParameter("i", t.getInventoryItemId()).getSingleResult();
            out.add(new Line(t.getInventoryTransferId(), (String) p[0], (String) p[1], t.getQuantity(), t.getStatusId(), t.getComments()));
        }
        return out;
    }

    private static String user() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
