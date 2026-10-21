package com.nexus.core.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.service.OrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes WMS_SHORT_PICK (doc 27, section 3.3): the facility's hold is released for the short quantity and the facility
 * excluded for the item. Items with nothing left held go back to routing: the whole ship group when nothing in it is
 * held any more, otherwise those items move to a new ship group in the routing queue.
 */
@Component("coreWmsShortPickConsumer")
public class WmsShortPickConsumer implements CoreMessageHandler {

    private final OrderService orders;
    private final ObjectMapper json = new ObjectMapper();

    public WmsShortPickConsumer(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public void consume(String systemMessageId, String messageText) {
        JsonNode m;
        try {
            m = json.readTree(messageText);
        } catch (Exception e) {
            throw new IllegalArgumentException("The short pick is not JSON: " + e.getMessage());
        }
        String orderId = WmsShipmentConsumer.required(m, "orderId");
        String group = WmsShipmentConsumer.required(m, "shipGroupSeqId");
        OrderItemShipGroup g = orders.shipGroup(orderId, group);
        String facility = g.getFacilityId();
        if (facility == null) throw new IllegalStateException("Ship group " + group + " of order " + orderId + " is not at a facility");
        String reason = WmsShipmentConsumer.text(m, "reasonEnumId");
        for (JsonNode i : m.path("items")) {
            orders.shortPick(orderId, WmsShipmentConsumer.required(i, "orderItemSeqId"), facility, new BigDecimal(i.path("shortQuantity").asText()),
                    reason == null ? "WMS short pick" : "WMS short pick: " + reason, "wms");
        }
        List<String> unheld = new ArrayList<>();
        boolean anyHeld = false;
        for (OrderItem i : orders.items(orderId)) {
            if (!group.equals(i.getShipGroupSeqId()) || !"ITEM_APPROVED".equals(i.getStatusId())) continue;
            if (orders.reservedQuantity(orderId, i.getOrderItemSeqId()).signum() > 0) anyHeld = true;
            else unheld.add(i.getOrderItemSeqId());
        }
        if (unheld.isEmpty()) return;
        if (!anyHeld) orders.setShipGroupFacility(orderId, group, null);
        else orders.splitShipGroup(orderId, group, unheld, null);
    }
}
