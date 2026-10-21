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

/** Consumes WMS_SHIPMENT_CONFIRMATION (doc 27, section 3.4) into a shipped shipment. Safe to consume twice. */
@Component("coreWmsShipmentConsumer")
public class WmsShipmentConsumer implements CoreMessageHandler {

    private final ShipmentService shipments;
    private final ObjectMapper json = new ObjectMapper();

    public WmsShipmentConsumer(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @Override
    public void consume(String systemMessageId, String messageText) {
        JsonNode m;
        try {
            m = json.readTree(messageText);
        } catch (Exception e) {
            throw new IllegalArgumentException("The shipment confirmation is not JSON: " + e.getMessage());
        }
        List<ShipmentService.ConfirmedPackage> packages = new ArrayList<>();
        for (JsonNode p : m.path("packages")) {
            Map<String, BigDecimal> contents = new LinkedHashMap<>();
            for (JsonNode c : p.path("contents")) contents.merge(text(c, "orderItemSeqId"), new BigDecimal(c.path("quantity").asText()), BigDecimal::add);
            packages.add(new ShipmentService.ConfirmedPackage(text(p, "trackingCode"), p.hasNonNull("weight") ? new BigDecimal(p.get("weight").asText()) : null,
                    text(p, "weightUomId"), text(p, "boxTypeId"), contents));
        }
        String shipped = text(m, "shippedAt");
        LocalDateTime at = shipped == null ? null : OffsetDateTime.parse(shipped).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        shipments.confirm(new ShipmentService.Confirmation(required(m, "shipmentId"), required(m, "orderId"), required(m, "shipGroupSeqId"), at,
                text(m, "carrierPartyId"), text(m, "shipmentMethodTypeId"), packages), "wms");
    }

    static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    static String required(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null) throw new IllegalArgumentException("The message has no " + field);
        return v;
    }
}
