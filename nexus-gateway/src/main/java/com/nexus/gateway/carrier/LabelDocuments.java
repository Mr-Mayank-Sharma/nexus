package com.nexus.gateway.carrier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every label the gateway made ({@code label_document}), so a client can print it again or void it later. */
@Repository
public class LabelDocuments {

    private final JdbcTemplate jdbc;

    public LabelDocuments(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A saved label: its format, the image as base64, and whether it was voided. */
    public record Document(String format, String contentBase64, boolean voided) {
    }

    /** Saves one box's label and returns its new id. */
    String save(String clientId, String storeId, String carrier, String carrierShipmentId, Shipping.LabelPackage labelPackage) {
        String labelId = UUID.randomUUID().toString();
        String content = labelPackage.labelBase64() == null ? "" : labelPackage.labelBase64();
        jdbc.update("INSERT INTO label_document (label_id, client_id, store_id, carrier, tracking_number, carrier_shipment_id, image_format, content_base64) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", labelId, clientId, storeId, carrier, labelPackage.trackingNumber(), carrierShipmentId, labelPackage.format(),
                content);
        return labelId;
    }

    /** The client's label by id; another client's label is not found. */
    Optional<Document> find(String clientId, String labelId) {
        return jdbc.queryForList("SELECT image_format, content_base64, voided FROM label_document WHERE label_id = ? AND client_id = ?", labelId, clientId)
                .stream()
                .findFirst()
                .map(row -> new Document((String) row.get("image_format"), (String) row.get("content_base64"), Boolean.TRUE.equals(row.get("voided"))));
    }

    /** The carrier's shipment id for a tracking number the client labelled with that carrier. */
    Optional<String> carrierShipmentIdOf(String clientId, String carrier, String trackingNumber) {
        List<String> shipmentIds = jdbc.queryForList("SELECT carrier_shipment_id FROM label_document WHERE client_id = ? AND carrier = ? AND tracking_number = ?",
                String.class, clientId, carrier, trackingNumber);
        return shipmentIds.stream().findFirst();
    }

    /** Marks every label of the carrier shipment voided. */
    void markVoided(String clientId, String carrier, String carrierShipmentId) {
        jdbc.update("UPDATE label_document SET voided = TRUE WHERE client_id = ? AND carrier = ? AND carrier_shipment_id = ?", clientId, carrier, carrierShipmentId);
    }
}
