package com.nexus.oms.integration.carrier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from a carrier after a shipment is created.
 * Contains the tracking number and the printable label (ZPL or PDF, base64).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierShipmentResponse {

    private String trackingNumber;
    private String labelBase64;   // ZPL or PDF bytes, base64-encoded
    private String labelFormat;   // ZPL | PDF
    private String labelUrl;      // optional hosted URL from the carrier
    private String rawResponse;   // raw carrier payload for audit/debug
    private boolean success;

    public static CarrierShipmentResponse failed(String rawResponse) {
        return CarrierShipmentResponse.builder()
                .success(false)
                .rawResponse(rawResponse)
                .build();
    }
}