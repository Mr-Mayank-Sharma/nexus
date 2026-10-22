package com.nexus.oms.coreapi;

import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.service.OrderReservationService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ShipmentService;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import com.nexus.core.CoreTransactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Orders and shipments on the core model: create, change status, hold stock, read; make, ship and confirm shipments.
 * Admin only ({@code /core/}). The order screens move onto these after the core is compared with the earlier model.
 * <p>
 * The controller only translates HTTP to service calls; every rule lives in the services. Each endpoint is one
 * transaction, so a failure part-way leaves nothing behind.
 */
@RestController
@RequestMapping("/core")
@Tag(name = "Core orders", description = "Orders and shipments on the OFBiz-aligned core model")
public class CoreOrdersController {

    private final OrderService orderService;
    private final OrderReservationService reservationService;
    private final ShipmentService shipmentService;

    public CoreOrdersController(OrderService orderService, OrderReservationService reservationService, ShipmentService shipmentService) {
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.shipmentService = shipmentService;
    }

    // ── orders ──

    @Operation(summary = "Create an order")
    @PostMapping("/orders")
    @CoreTransactional
    public ResponseEntity<ApiResponse<String>> create(@RequestBody OrderService.NewOrder newOrder) {
        String orderId = orderService.create(newOrder);
        return ResponseEntity.ok(ApiResponse.success(orderId, "Order created"));
    }

    public record StatusChange(String statusId, String reason) {
    }

    @Operation(summary = "Move an order to a status its flow allows")
    @PostMapping("/orders/{orderId}/status")
    @CoreTransactional
    public ResponseEntity<ApiResponse<Void>> status(@PathVariable String orderId, @RequestBody StatusChange change) {
        orderService.changeStatus(orderId, change.statusId(), change.reason(), currentUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Status changed"));
    }

    public record Hold(String facilityId, BigDecimal quantity) {
    }

    @Operation(summary = "Hold stock for an item at a facility (all that is open when no quantity)")
    @PostMapping("/orders/{orderId}/items/{orderItemSeqId}/reserve")
    @CoreTransactional
    public ResponseEntity<ApiResponse<Void>> reserve(@PathVariable String orderId, @PathVariable String orderItemSeqId, @RequestBody Hold hold) {
        reservationService.reserve(orderId, orderItemSeqId, hold.facilityId(), hold.quantity(), currentUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Held"));
    }

    public record ItemView(String orderItemSeqId, String productId, String description, BigDecimal quantity, BigDecimal cancelQuantity,
                           BigDecimal unitPrice, String statusId, String shipGroupSeqId, BigDecimal reserved) {
    }

    public record OrderView(String orderId, String statusId, String productStoreId, String externalId, BigDecimal grandTotal, String currencyUomId,
                            String orderDate, List<ItemView> items) {
    }

    @Operation(summary = "An order with its items")
    @GetMapping("/orders/{orderId}")
    @CoreTransactional(readOnly = true)
    public ResponseEntity<ApiResponse<OrderView>> get(@PathVariable String orderId) {
        OrderHeader header = orderService.header(orderId);
        List<ItemView> items = orderService.items(orderId).stream()
                .map(orderItem -> toItemView(orderId, orderItem))
                .toList();
        OrderView view = new OrderView(orderId, header.getStatusId(), header.getProductStoreId(), header.getExternalId(), header.getGrandTotal(),
                header.getCurrencyUomId(), String.valueOf(header.getOrderDate()), items);
        return ResponseEntity.ok(ApiResponse.success(view, "Order"));
    }

    private ItemView toItemView(String orderId, OrderItem orderItem) {
        BigDecimal reserved = reservationService.reservedQuantity(orderId, orderItem.getOrderItemSeqId());
        return new ItemView(orderItem.getOrderItemSeqId(), orderItem.getProductId(), orderItem.getItemDescription(), orderItem.getQuantity(),
                orderItem.getCancelQuantity(), orderItem.getUnitPrice(), orderItem.getStatusId(), orderItem.getShipGroupSeqId(), reserved);
    }

    // ── shipments ──

    public record NewShipment(String orderId, String shipGroupSeqId, Map<String, BigDecimal> quantities) {
    }

    @Operation(summary = "Make a shipment for a ship group (everything open when no quantities)")
    @PostMapping("/shipments")
    @CoreTransactional
    public ResponseEntity<ApiResponse<String>> shipment(@RequestBody NewShipment newShipment) {
        String shipmentId = shipmentService.createSalesShipment(newShipment.orderId(), newShipment.shipGroupSeqId(), newShipment.quantities(), currentUser());
        return ResponseEntity.ok(ApiResponse.success(shipmentId, "Shipment made"));
    }

    public record Ship(String carrierPartyId, String shipmentMethodTypeId, Map<String, String> trackingByPackage) {
    }

    @Operation(summary = "Ship a shipment: stock leaves, items complete; tracking from its labels is kept")
    @PostMapping("/shipments/{shipmentId}/ship")
    @CoreTransactional
    public ResponseEntity<ApiResponse<String>> ship(@PathVariable String shipmentId, @RequestBody(required = false) Ship request) {
        Ship details = request == null ? new Ship(null, null, null) : request;
        shipmentService.ship(shipmentId, details.carrierPartyId(), details.shipmentMethodTypeId(), details.trackingByPackage(), null, currentUser());
        return ResponseEntity.ok(ApiResponse.success(shipmentService.status(shipmentId), "Shipped"));
    }

    @Operation(summary = "Record a shipment another system made (the WMS confirmation of doc 27)")
    @PostMapping("/shipments/confirm")
    @CoreTransactional
    public ResponseEntity<ApiResponse<String>> confirm(@RequestBody ShipmentService.Confirmation confirmation) {
        String shipmentId = shipmentService.confirm(confirmation, currentUser());
        return ResponseEntity.ok(ApiResponse.success(shipmentId, "Shipment recorded"));
    }

    /** The signed-in user, or null outside a request (a job, a test). */
    private static String currentUser() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException noUser) {
            return null;
        }
    }
}
