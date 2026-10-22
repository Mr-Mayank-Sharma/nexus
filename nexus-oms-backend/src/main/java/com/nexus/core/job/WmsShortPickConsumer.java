package com.nexus.core.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.CoreStatus;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.service.OrderReservationService;
import com.nexus.core.service.OrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes WMS_SHORT_PICK (doc 27, section 3.3): the WMS could not find some of what the facility held.
 * <ol>
 *   <li>For each short item, the facility's hold is released for the short quantity and the facility is excluded for
 *       the item.</li>
 *   <li>Items of the ship group that now hold nothing go back to routing: the whole ship group when nothing in it is
 *       held any more, otherwise just those items, moved to a new ship group in the routing queue.</li>
 * </ol>
 */
@Component("coreWmsShortPickConsumer")
public class WmsShortPickConsumer implements CoreMessageHandler {

    static final String WMS_USER = "wms";

    private final OrderService orderService;
    private final OrderReservationService reservationService;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public WmsShortPickConsumer(OrderService orderService, OrderReservationService reservationService) {
        this.orderService = orderService;
        this.reservationService = reservationService;
    }

    @Override
    public void consume(String systemMessageId, String messageText) {
        JsonNode message;
        try {
            message = jsonMapper.readTree(messageText);
        } catch (Exception notJson) {
            throw new IllegalArgumentException("The short pick is not JSON: " + notJson.getMessage());
        }
        String orderId = WmsShipmentConsumer.required(message, "orderId");
        String shipGroupSeqId = WmsShipmentConsumer.required(message, "shipGroupSeqId");
        OrderItemShipGroup shipGroup = orderService.shipGroup(orderId, shipGroupSeqId);
        String facilityId = shipGroup.getFacilityId();
        if (facilityId == null) {
            throw new IllegalStateException("Ship group " + shipGroupSeqId + " of order " + orderId + " is not at a facility");
        }

        // Step 1: release what was not found, and keep routing away from this facility for those items.
        String reasonCode = WmsShipmentConsumer.text(message, "reasonEnumId");
        String note = reasonCode == null ? "WMS short pick" : "WMS short pick: " + reasonCode;
        for (JsonNode shortItem : message.path("items")) {
            String orderItemSeqId = WmsShipmentConsumer.required(shortItem, "orderItemSeqId");
            BigDecimal shortQuantity = new BigDecimal(shortItem.path("shortQuantity").asText());
            reservationService.shortPick(orderId, orderItemSeqId, facilityId, shortQuantity, note, WMS_USER);
        }

        // Step 2: which approved items of the group still hold stock, and which hold nothing now.
        List<String> itemsHoldingNothing = new ArrayList<>();
        boolean anyItemStillHeld = false;
        for (OrderItem orderItem : orderService.items(orderId)) {
            boolean inThisGroup = shipGroupSeqId.equals(orderItem.getShipGroupSeqId());
            if (!inThisGroup || !CoreStatus.Item.APPROVED.equals(orderItem.getStatusId())) {
                continue;
            }
            if (reservationService.reservedQuantity(orderId, orderItem.getOrderItemSeqId()).signum() > 0) {
                anyItemStillHeld = true;
            } else {
                itemsHoldingNothing.add(orderItem.getOrderItemSeqId());
            }
        }

        // Step 3: send what holds nothing back to routing.
        if (itemsHoldingNothing.isEmpty()) {
            return;
        }
        if (anyItemStillHeld) {
            orderService.splitShipGroup(orderId, shipGroupSeqId, itemsHoldingNothing, null);
        } else {
            orderService.setShipGroupFacility(orderId, shipGroupSeqId, null);
        }
    }
}
