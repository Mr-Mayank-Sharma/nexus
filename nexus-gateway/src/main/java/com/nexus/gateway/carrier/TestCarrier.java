package com.nexus.gateway.carrier;

import com.nexus.gateway.account.Accounts.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * NEXUS_TEST: a carrier that needs no account at a real carrier, so an installation can run labels, tracking and
 * address checks end to end before its carrier accounts exist. Prices follow weight; labels are marked TEST.
 */
@Component
public class TestCarrier implements CarrierAdapter {

    // Price = base + per kg.
    static final BigDecimal GROUND_BASE = BigDecimal.valueOf(5);
    static final BigDecimal GROUND_PER_KG = BigDecimal.valueOf(1.5);
    static final BigDecimal OVERNIGHT_BASE = BigDecimal.valueOf(18);
    static final BigDecimal OVERNIGHT_PER_KG = BigDecimal.valueOf(3);
    /** The story a test tracking number tells, in order. */
    private static final List<String> TRACKING_STEPS = List.of("LABEL_CREATED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");

    @Override
    public String code() {
        return "NEXUS_TEST";
    }

    @Override
    public List<Shipping.Quote> rates(Account account, Shipping.RateRequest request) {
        BigDecimal totalKg = totalWeightKg(request.parcels());
        BigDecimal groundPrice = GROUND_BASE.add(totalKg.multiply(GROUND_PER_KG)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overnightPrice = OVERNIGHT_BASE.add(totalKg.multiply(OVERNIGHT_PER_KG)).setScale(2, RoundingMode.HALF_UP);
        return List.of(
                new Shipping.Quote(code(), "STANDARD", "Test ground", groundPrice, "USD", 4),
                new Shipping.Quote(code(), "NEXT_DAY", "Test overnight", overnightPrice, "USD", 1));
    }

    @Override
    public Shipping.Label label(Account account, Shipping.LabelRequest request) {
        if (request.parcels() == null || request.parcels().isEmpty()) {
            throw new CarrierException(400, "A label needs at least one parcel");
        }
        if (request.to() == null || request.to().postalCode() == null || request.to().postalCode().isBlank()) {
            throw new CarrierException(400, "The destination has no postal code");
        }
        List<Shipping.LabelPackage> labelPackages = new ArrayList<>();
        for (int parcelIndex = 0; parcelIndex < request.parcels().size(); parcelIndex++) {
            String trackingNumber = "NXT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
            String labelText = (request.isReturn() ? "TEST RETURN LABEL" : "TEST LABEL") + " " + trackingNumber + " to " + request.to().postalCode();
            labelPackages.add(new Shipping.LabelPackage(trackingNumber, Base64.getEncoder().encodeToString(labelText.getBytes(StandardCharsets.UTF_8)), "TXT"));
        }
        BigDecimal cost = rates(account, new Shipping.RateRequest(request.from(), request.to(), request.parcels())).get(0).amount();
        String firstTrackingNumber = labelPackages.get(0).trackingNumber();
        return new Shipping.Label("NXS-" + firstTrackingNumber, firstTrackingNumber, labelPackages, cost, "USD");
    }

    @Override
    public boolean voidLabel(Account account, String carrierShipmentId, String trackingNumber) {
        return true;
    }

    @Override
    public Shipping.AddressCheck validate(Account account, Shipping.Address address) {
        List<String> problems = new ArrayList<>();
        boolean noStreet = address.lines() == null || address.lines().isEmpty() || address.lines().get(0).isBlank();
        if (noStreet) {
            problems.add("Street address is missing");
        }
        if (address.postalCode() == null || address.postalCode().isBlank()) {
            problems.add("Postal code is missing");
        }
        if (address.countryCode() == null || address.countryCode().length() != 2) {
            problems.add("Country must be a two-letter code");
        }
        return new Shipping.AddressCheck(problems.isEmpty(), false, null, null, problems);
    }

    @Override
    public Shipping.Tracking track(Account account, String trackingNumber) {
        // The number's hash picks the step, so a test number always tells the same story.
        String status = TRACKING_STEPS.get(Math.abs(trackingNumber.hashCode()) % TRACKING_STEPS.size());
        return new Shipping.Tracking(trackingNumber, status, "TEST_" + status, null, List.of(new Shipping.TrackEvent(null, status, "Test event", "Test city")));
    }

    /** Total weight; a parcel without a weight counts as 1 kg. */
    static BigDecimal totalWeightKg(List<Shipping.Parcel> parcels) {
        BigDecimal totalKg = BigDecimal.ZERO;
        for (Shipping.Parcel parcel : parcels == null ? List.<Shipping.Parcel>of() : parcels) {
            totalKg = totalKg.add(parcel.weightKg() == null ? BigDecimal.ONE : parcel.weightKg());
        }
        return totalKg;
    }
}
