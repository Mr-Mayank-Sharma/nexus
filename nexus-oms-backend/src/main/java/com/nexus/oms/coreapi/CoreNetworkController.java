package com.nexus.oms.coreapi;

import com.nexus.core.service.OperationsReadService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.StoreHealthService;
import com.nexus.core.service.StoreNetworkService;
import com.nexus.core.service.TransferService;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The store network on the core model (doc 24, section 4b): transfers from request to receipt, stock lookup across
 * stores with confidence, holds for pickup, sales credit and rebalancing suggestions; and the store health scores.
 * Admin only ({@code /core/}). The controller holds no queries: every read and change is a service call.
 */
@RestController
@RequestMapping("/core/network")
@Tag(name = "Core store network", description = "Transfers, lookup with confidence, pickup holds, sales credit, rebalancing")
public class CoreNetworkController {

    static final int MAX_PHANTOM_ROWS = 500;
    static final int PRODUCT_SEARCH_LIMIT = 20;
    static final int RECENT_TRANSFERS = 100;

    private final TransferService transferService;
    private final StoreNetworkService storeNetworkService;
    private final StoreHealthService storeHealthService;
    private final OperationsReadService operationsReadService;
    private final ProductService productService;

    public CoreNetworkController(TransferService transferService, StoreNetworkService storeNetworkService, StoreHealthService storeHealthService,
                                 OperationsReadService operationsReadService, ProductService productService) {
        this.transferService = transferService;
        this.storeNetworkService = storeNetworkService;
        this.storeHealthService = storeHealthService;
        this.operationsReadService = operationsReadService;
        this.productService = productService;
    }

    // ── store health ──

    @Operation(summary = "How reliably each location picks the orders routed to it, least reliable first")
    @GetMapping("/reliability")
    public ResponseEntity<ApiResponse<List<StoreHealthService.Reliability>>> reliability(@RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(ApiResponse.success(storeHealthService.reliability(days), "Reliability"));
    }

    @Operation(summary = "Stock most likely not really there, highest risk first: where a targeted count pays")
    @GetMapping("/phantom-stock")
    public ResponseEntity<ApiResponse<List<StoreHealthService.PhantomRisk>>> phantom(@RequestParam(required = false) String facilityId,
                                                                                   @RequestParam(defaultValue = "30") int days,
                                                                                   @RequestParam(defaultValue = "50") int limit) {
        List<StoreHealthService.PhantomRisk> risks = storeHealthService.phantomStock(facilityId, days, Math.min(limit, MAX_PHANTOM_ROWS));
        return ResponseEntity.ok(ApiResponse.success(risks, "Phantom stock"));
    }

    // ── pickers ──

    public record Location(String facilityId, String facilityName, String facilityTypeId) {
    }

    @Operation(summary = "Locations that can hold stock (not the virtual ones)")
    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<List<Location>>> locations() {
        List<Location> locations = operationsReadService.locations().stream()
                .map(location -> new Location(location.facilityId(), location.facilityName(), location.facilityTypeId()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(locations, "Locations"));
    }

    @Operation(summary = "Products with stock at a location, for picking what to transfer")
    @GetMapping("/locations/{facilityId}/products")
    public ResponseEntity<ApiResponse<List<StoreNetworkService.StockAtLocation>>> productsAt(@PathVariable String facilityId,
                                                                                           @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(ApiResponse.success(storeNetworkService.stockAt(facilityId, q), "Products"));
    }

    @Operation(summary = "Find products by name or SKU")
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductService.ProductRef>>> products(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(productService.search(q, PRODUCT_SEARCH_LIMIT), "Products"));
    }

    // ── transfers ──

    public record NewTransfer(String fromFacilityId, String toFacilityId, List<TransferService.Line> lines, String comments) {
    }

    public record Settle(String productId, BigDecimal found, String note) {
    }

    @Operation(summary = "Transfers, newest first")
    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<List<TransferService.TransferView>>> list() {
        return ResponseEntity.ok(ApiResponse.success(transferService.recentTransfers(RECENT_TRANSFERS), "Transfers"));
    }

    @Operation(summary = "Request a transfer")
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<String>> request(@RequestBody NewTransfer newTransfer) {
        String shipmentId = transferService.request(newTransfer.fromFacilityId(), newTransfer.toFacilityId(), newTransfer.lines(), newTransfer.comments(),
                currentUser());
        return ResponseEntity.ok(ApiResponse.success(shipmentId, "Transfer requested"));
    }

    @Operation(summary = "Approve: the stock is held at the sending location")
    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable String id) {
        transferService.approve(id, currentUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Approved; stock held at the sending location"));
    }

    @Operation(summary = "Ship: the stock leaves the sending location and is in transit")
    @PostMapping("/transfers/{id}/ship")
    public ResponseEntity<ApiResponse<Void>> ship(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String trackingNumber = body == null ? null : body.get("trackingNumber");
        transferService.ship(id, trackingNumber, currentUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Shipped; in transit"));
    }

    @Operation(summary = "Receive by scan: quantity received per product")
    @PostMapping("/transfers/{id}/receive")
    public ResponseEntity<ApiResponse<List<TransferService.Receipt>>> receive(@PathVariable String id, @RequestBody Map<String, BigDecimal> scannedByProduct) {
        return ResponseEntity.ok(ApiResponse.success(transferService.receive(id, scannedByProduct, currentUser()), "Received"));
    }

    @Operation(summary = "Settle a difference: found units go on sale, the rest is written off as lost in transit")
    @PostMapping("/transfers/{id}/settle")
    public ResponseEntity<ApiResponse<Void>> settle(@PathVariable String id, @RequestBody Settle settle) {
        transferService.settle(id, settle.productId(), settle.found(), settle.note(), currentUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Settled"));
    }

    @Operation(summary = "Cancel a transfer not yet shipped")
    @PostMapping("/transfers/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable String id) {
        transferService.cancel(id, currentUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Cancelled"));
    }

    // ── lookup, holds, credit, rebalancing ──

    @Operation(summary = "Which stores can sell a product now, with a confidence level and its reasons")
    @GetMapping("/lookup/{productId}")
    public ResponseEntity<ApiResponse<List<StoreNetworkService.Availability>>> lookup(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(storeNetworkService.lookup(productId), "Availability"));
    }

    public record Hold(String productStoreId, String productId, String facilityId, BigDecimal quantity, String customerPartyId, long holdHours,
                       String sellingFacilityId) {
    }

    @Operation(summary = "Hold a product at a store for a customer to collect")
    @PostMapping("/holds")
    public ResponseEntity<ApiResponse<String>> hold(@RequestBody Hold hold) {
        String orderId = storeNetworkService.holdForPickup(hold.productStoreId(), hold.productId(), hold.facilityId(), hold.quantity(),
                hold.customerPartyId(), hold.holdHours(), hold.sellingFacilityId(), currentUser());
        return ResponseEntity.ok(ApiResponse.success(orderId, "Held for pickup"));
    }

    @Operation(summary = "Sales credit of an order between the selling and the fulfilling store")
    @GetMapping("/orders/{orderId}/sales-credit")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> credit(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(storeNetworkService.salesCredit(orderId), "Sales credit"));
    }

    @Operation(summary = "Transfers worth making inside a store cluster before stock is marked down")
    @GetMapping("/clusters/{facilityGroupId}/rebalance")
    public ResponseEntity<ApiResponse<List<StoreNetworkService.Suggestion>>> rebalance(@PathVariable String facilityGroupId,
                                                                                      @RequestParam(defaultValue = "2") double lowWeeks,
                                                                                      @RequestParam(defaultValue = "12") double highWeeks,
                                                                                      @RequestParam(defaultValue = "4") double targetWeeks) {
        List<StoreNetworkService.Suggestion> suggestions = storeNetworkService.rebalance(facilityGroupId, lowWeeks, highWeeks, targetWeeks);
        return ResponseEntity.ok(ApiResponse.success(suggestions, "Suggestions"));
    }

    /** The signed-in user, or null outside a request. */
    private static String currentUser() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException noUser) {
            return null;
        }
    }
}
