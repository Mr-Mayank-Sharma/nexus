package com.nexus.oms.integration.carrier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request payload for creating a shipment in a carrier (Jitsu, SAPI, VHO...).
 * Carries everything a carrier needs to produce a shipping label.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierShipmentRequest {

    private UUID tenantId;
    private UUID orderId;
    private String orderNumber;

    private String carrierCode;   // JITSU, SAPI, VHO, FEDEX, UPS...
    private String serviceType;   // STANDARD, EXPRESS, OVERNIGHT, SAME_DAY

    private String fromName;
    private String fromAddress;

    private String toName;
    private String toAddress;

    private Double weight;        // lb
    private String dimensions;    // LxWxH in inches

    private String reference;     // optional carrier reference / PO number
}