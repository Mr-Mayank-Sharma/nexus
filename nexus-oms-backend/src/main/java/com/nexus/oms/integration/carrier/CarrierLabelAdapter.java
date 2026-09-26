package com.nexus.oms.integration.carrier;

import com.nexus.oms.entity.NxShippingLabel;

import java.util.List;

/**
 * Pluggable carrier-label adapter.
 *
 * Adding a new carrier (Jitsu, SAPI, VHO, UPS, FedEx...) is a new adapter bean
 * plus a {@code nx_carrier_label_config} row — no changes to the OMS core.
 */
public interface CarrierLabelAdapter {

    /** Bean name, e.g. "JitsuCarrierAdapter". Must match {@code nx_carrier_label_config.adapter_name}. */
    String getName();

    /** "ZPL" or "PDF" — what this carrier returns for printable labels. */
    String getLabelFormat();

    /** Create a shipment in the carrier and return a printable label. */
    CarrierShipmentResponse createShipment(CarrierShipmentRequest request);

    /** Required-field validation. Returns the list of missing fields (empty = valid). */
    List<String> validateLabel(NxShippingLabel label);
}