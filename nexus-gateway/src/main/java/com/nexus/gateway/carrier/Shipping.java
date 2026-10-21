package com.nexus.gateway.carrier;

import java.math.BigDecimal;
import java.util.List;

/** The one shipping vocabulary every carrier is translated to and from. Weights in kg, sizes in cm. */
public final class Shipping {

    private Shipping() {
    }

    public record Address(String name, String company, String phone, String email, List<String> lines, String city, String state,
                          String postalCode, String countryCode, Boolean residential) {
    }

    public record Parcel(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
    }

    public record RateRequest(Address from, Address to, List<Parcel> parcels) {
    }

    public record Quote(String carrier, String service, String serviceName, BigDecimal amount, String currency, Integer transitDays) {
    }

    public record LabelRequest(String service, Address from, Address to, List<Parcel> parcels, String reference, String imageFormat, boolean isReturn) {
    }

    public record LabelPackage(String trackingNumber, String labelBase64, String format) {
    }

    public record Label(String carrierShipmentId, String masterTrackingNumber, List<LabelPackage> packages, BigDecimal cost, String currency) {
    }

    public record TrackEvent(String at, String code, String description, String location) {
    }

    /** Status is one of LABEL_CREATED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION, UNKNOWN; the carrier's own is kept too. */
    public record Tracking(String trackingNumber, String status, String carrierStatus, String estimatedDelivery, List<TrackEvent> events) {
    }

    public record AddressCheck(boolean valid, boolean ambiguous, Address suggested, Boolean residential, List<String> messages) {
    }
}
