package com.nexus.oms.integration.carrier;

import com.nexus.oms.entity.NxShippingLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Simulated carrier adapter — the existing local SIMULATED path.
 * Fallback for dev / no carrier configured. Produces a fake tracking number
 * and a printable PDF payload so the full label flow works end-to-end offline.
 */
@Component
public class SimulatedCarrierAdapter implements CarrierLabelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedCarrierAdapter.class);

    @Override
    public String getName() {
        return "SimulatedCarrierAdapter";
    }

    @Override
    public String getLabelFormat() {
        return "PDF";
    }

    @Override
    public CarrierShipmentResponse createShipment(CarrierShipmentRequest request) {
        String prefix = switch (request.getCarrierCode() != null ? request.getCarrierCode().toUpperCase() : "GEN") {
            case "FEDEX" -> "FX";
            case "UPS" -> "UP";
            case "USPS" -> "US";
            case "DHL" -> "DH";
            case "JITSU" -> "JT";
            case "SAPI" -> "SP";
            case "LOCAL" -> "LC";
            default -> "GN";
        };
        String tracking = prefix + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));

        // Minimal PDF (valid %PDF-1.4 header) so the UI can download/preview.
        String pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF";
        String labelBase64 = Base64.getEncoder().encodeToString(pdf.getBytes());

        log.info("Simulated shipment created: tracking={}", tracking);
        return CarrierShipmentResponse.builder()
                .success(true)
                .trackingNumber(tracking)
                .labelBase64(labelBase64)
                .labelFormat(getLabelFormat())
                .rawResponse("{\"simulated\":true}")
                .build();
    }

    @Override
    public List<String> validateLabel(NxShippingLabel label) {
        return Collections.emptyList();
    }
}