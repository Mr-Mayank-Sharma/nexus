package com.nexus.core.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.service.ShipmentService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumes WMS_SHIPMENT_CONFIRMATION (doc 27, section 3.4) into a shipped shipment: the boxes with their tracking codes
 * and contents (quantity per order item), the carrier and when it shipped. Safe to consume twice: the shipment service
 * recognises the same WMS shipment id.
 */
@Component("coreWmsShipmentConsumer")
public class WmsShipmentConsumer implements CoreMessageHandler {

    static final String WMS_USER = "wms";

    private final ShipmentService shipmentService;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public WmsShipmentConsumer(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Override
    public void consume(String systemMessageId, String messageText) {
        JsonNode message;
        try {
            message = jsonMapper.readTree(messageText);
        } catch (Exception notJson) {
            throw new IllegalArgumentException("The shipment confirmation is not JSON: " + notJson.getMessage());
        }

        List<ShipmentService.ConfirmedPackage> packages = new ArrayList<>();
        for (JsonNode packageNode : message.path("packages")) {
            Map<String, BigDecimal> quantityByOrderItem = new LinkedHashMap<>();
            for (JsonNode content : packageNode.path("contents")) {
                quantityByOrderItem.merge(text(content, "orderItemSeqId"), new BigDecimal(content.path("quantity").asText()), BigDecimal::add);
            }
            BigDecimal weight = packageNode.hasNonNull("weight") ? new BigDecimal(packageNode.get("weight").asText()) : null;
            packages.add(new ShipmentService.ConfirmedPackage(text(packageNode, "trackingCode"), weight, text(packageNode, "weightUomId"),
                    text(packageNode, "boxTypeId"), quantityByOrderItem));
        }

        // Times arrive with an offset; the core keeps UTC.
        String shippedAtText = text(message, "shippedAt");
        LocalDateTime shippedAt = shippedAtText == null
                ? null
                : OffsetDateTime.parse(shippedAtText).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();

        ShipmentService.Confirmation confirmation = new ShipmentService.Confirmation(required(message, "shipmentId"), required(message, "orderId"),
                required(message, "shipGroupSeqId"), shippedAt, text(message, "carrierPartyId"), text(message, "shipmentMethodTypeId"), packages);
        shipmentService.confirm(confirmation, WMS_USER);
    }

    /** A text field, or null when it is missing, null or blank. */
    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    /** A text field that must be there. */
    static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("The message has no " + field);
        }
        return value;
    }
}
