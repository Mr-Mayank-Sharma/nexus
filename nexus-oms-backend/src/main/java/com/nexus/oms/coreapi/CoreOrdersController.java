package com.nexus.oms.coreapi;

import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ShipmentService;
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
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Orders and shipments on the core model: create, change status, hold stock, read; make, pack, ship and confirm
 * shipments. Admin only ({@code /core/}). The order screens move onto these after the core is compared with the
 * earlier model.
 */
@RestController
@RequestMapping("/core")
@Tag(name = "Core orders", description = "Orders and shipments on the OFBiz-aligned core model")
public class CoreOrdersController {

    private final OrderService orders;
    private final ShipmentService shipments;
    private final EntityManager em;

    public CoreOrdersController(OrderService orders, ShipmentService shipments, EntityManager em) {
        this.orders = orders;
        this.shipments = shipments;
        this.em = em;
    }

    @Operation(summary = "Create an order")
    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<ApiResponse<String>> create(@RequestBody OrderService.NewOrder n) {
        return ResponseEntity.ok(ApiResponse.success(orders.create(n), "Order created"));
    }

    public record StatusChange(String statusId, String reason) {
    }

    @Operation(summary = "Move an order to a status its flow allows")
    @PostMapping("/orders/{orderId}/status")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> status(@PathVariable String orderId, @RequestBody StatusChange c) {
        orders.changeStatus(orderId, c.statusId(), c.reason(), user());
        return ResponseEntity.ok(ApiResponse.success(null, "Status changed"));
    }

    public record Hold(String facilityId, BigDecimal quantity) {
    }

    @Operation(summary = "Hold stock for an item at a facility (all that is open when no quantity)")
    @PostMapping("/orders/{orderId}/items/{orderItemSeqId}/reserve")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> reserve(@PathVariable String orderId, @PathVariable String orderItemSeqId, @RequestBody Hold h) {
        orders.reserve(orderId, orderItemSeqId, h.facilityId(), h.quantity(), user());
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
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<OrderView>> get(@PathVariable String orderId) {
        OrderHeader h = em.find(OrderHeader.class, orderId);
        if (h == null) throw new IllegalArgumentException("Unknown order " + orderId);
        List<ItemView> items = orders.items(orderId).stream().map((OrderItem i) -> new ItemView(i.getOrderItemSeqId(), i.getProductId(), i.getItemDescription(),
                i.getQuantity(), i.getCancelQuantity(), i.getUnitPrice(), i.getStatusId(), i.getShipGroupSeqId(),
                orders.reservedQuantity(orderId, i.getOrderItemSeqId()))).toList();
        return ResponseEntity.ok(ApiResponse.success(new OrderView(orderId, h.getStatusId(), h.getProductStoreId(), h.getExternalId(), h.getGrandTotal(),
                h.getCurrencyUomId(), String.valueOf(h.getOrderDate()), items), "Order"));
    }

    public record NewShipment(String orderId, String shipGroupSeqId, Map<String, BigDecimal> quantities) {
    }

    @Operation(summary = "Make a shipment for a ship group (everything open when no quantities)")
    @PostMapping("/shipments")
    @Transactional
    public ResponseEntity<ApiResponse<String>> shipment(@RequestBody NewShipment s) {
        return ResponseEntity.ok(ApiResponse.success(shipments.createSalesShipment(s.orderId(), s.shipGroupSeqId(), s.quantities(), user()), "Shipment made"));
    }

    public record Ship(String carrierPartyId, String shipmentMethodTypeId, Map<String, String> trackingByPackage) {
    }

    @Operation(summary = "Ship a shipment: stock leaves, items complete; tracking from its labels is kept")
    @PostMapping("/shipments/{shipmentId}/ship")
    @Transactional
    public ResponseEntity<ApiResponse<String>> ship(@PathVariable String shipmentId, @RequestBody(required = false) Ship s) {
        shipments.ship(shipmentId, s == null ? null : s.carrierPartyId(), s == null ? null : s.shipmentMethodTypeId(), s == null ? null : s.trackingByPackage(), null, user());
        return ResponseEntity.ok(ApiResponse.success(shipments.status(shipmentId), "Shipped"));
    }

    @Operation(summary = "Record a shipment another system made (the WMS confirmation of doc 27)")
    @PostMapping("/shipments/confirm")
    @Transactional
    public ResponseEntity<ApiResponse<String>> confirm(@RequestBody ShipmentService.Confirmation c) {
        return ResponseEntity.ok(ApiResponse.success(shipments.confirm(c, user()), "Shipment recorded"));
    }

    private static String user() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
