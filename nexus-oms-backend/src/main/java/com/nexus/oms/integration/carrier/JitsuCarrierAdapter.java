package com.nexus.oms.integration.carrier;

import com.nexus.oms.entity.NxShippingLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.*;

/**
 * Jitsu carrier adapter — POSTs to the Jitsu API (JSON schema) to create a
 * shipment and fetch a printable label (ZPL or PDF, base64-encoded).
 *
 * Config-driven: {@code nexus.carrier.jitsu.base-url} + {@code nexus.carrier.jitsu.api-key}.
 * When not configured (dev), createShipment fails with a clear message so the UI
 * can surface "carrier not configured" instead of a silent SIMULATED fallback.
 */
@Component
public class JitsuCarrierAdapter implements CarrierLabelAdapter {

    private static final Logger log = LoggerFactory.getLogger(JitsuCarrierAdapter.class);

    private final RestTemplate restTemplate;

    @Value("${nexus.carrier.jitsu.base-url:}")
    private String baseUrl;

    @Value("${nexus.carrier.jitsu.api-key:}")
    private String apiKey;

    public JitsuCarrierAdapter() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getName() {
        return "JitsuCarrierAdapter";
    }

    @Override
    public String getLabelFormat() {
        return "ZPL";
    }

    @Override
    public CarrierShipmentResponse createShipment(CarrierShipmentRequest request) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return CarrierShipmentResponse.failed("Jitsu carrier not configured (nexus.carrier.jitsu.base-url missing)");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference", request.getOrderNumber());
        body.put("service", request.getServiceType());
        body.put("shipFrom", Map.of("name", request.getFromName(), "address", request.getFromAddress()));
        body.put("shipTo", Map.of("name", request.getToName(), "address", request.getToAddress()));
        body.put("weight", request.getWeight());
        body.put("dimensions", request.getDimensions());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/shipments",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return CarrierShipmentResponse.failed("Jitsu returned HTTP " + response.getStatusCode());
            }

            Map<?, ?> data = response.getBody();
            Object trackingObj = data.get("trackingNumber");
            if (trackingObj == null) trackingObj = data.get("tracking_number");
            Object labelObj = data.get("labelBase64");
            if (labelObj == null) labelObj = data.get("label_base64");
            String tracking = trackingObj != null ? String.valueOf(trackingObj) : "";
            String label = labelObj != null ? String.valueOf(labelObj) : "";

            if (tracking.isBlank() || label.isBlank()) {
                return CarrierShipmentResponse.failed("Jitsu response missing tracking/label: " + data);
            }

            log.info("Jitsu shipment created: tracking={} format={}", tracking, getLabelFormat());
            return CarrierShipmentResponse.builder()
                    .success(true)
                    .trackingNumber(tracking)
                    .labelBase64(label)
                    .labelFormat(getLabelFormat())
                    .rawResponse(String.valueOf(data))
                    .build();
        } catch (RestClientException e) {
            log.error("Jitsu createShipment failed: {}", e.getMessage(), e);
            return CarrierShipmentResponse.failed("Jitsu API error: " + e.getMessage());
        }
    }

    @Override
    public List<String> validateLabel(NxShippingLabel label) {
        List<String> missing = new ArrayList<>();
        if (isBlank(label.getFromName())) missing.add("ship-from name");
        if (isBlank(label.getFromAddress())) missing.add("ship-from address");
        if (isBlank(label.getToName())) missing.add("ship-to name");
        if (isBlank(label.getToAddress())) missing.add("ship-to address");
        if (isBlank(label.getServiceType())) missing.add("service");
        if (isBlank(label.getTrackingNumber())) missing.add("tracking");
        if (label.getWeight() == null) missing.add("weight");
        if (isBlank(label.getDimensions())) missing.add("dimensions");
        return missing;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}