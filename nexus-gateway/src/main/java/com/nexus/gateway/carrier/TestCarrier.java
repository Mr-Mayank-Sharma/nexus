package com.nexus.gateway.carrier;

import com.nexus.gateway.account.Accounts.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * NEXUS_TEST: a carrier that needs no account at a real carrier, so an installation can run labels, tracking and
 * address checks end to end before its carrier accounts exist. Prices follow weight; labels are marked TEST.
 */
@Component
public class TestCarrier implements CarrierAdapter {

    @Override
    public String code() {
        return "NEXUS_TEST";
    }

    @Override
    public List<Shipping.Quote> rates(Account account, Shipping.RateRequest r) {
        BigDecimal kg = weight(r.parcels());
        return List.of(
                new Shipping.Quote(code(), "STANDARD", "Test ground", BigDecimal.valueOf(5).add(kg.multiply(BigDecimal.valueOf(1.5))).setScale(2, RoundingMode.HALF_UP), "USD", 4),
                new Shipping.Quote(code(), "NEXT_DAY", "Test overnight", BigDecimal.valueOf(18).add(kg.multiply(BigDecimal.valueOf(3))).setScale(2, RoundingMode.HALF_UP), "USD", 1));
    }

    @Override
    public Shipping.Label label(Account account, Shipping.LabelRequest r) {
        if (r.parcels() == null || r.parcels().isEmpty()) throw new CarrierException(400, "A label needs at least one parcel");
        check(r.to());
        List<Shipping.LabelPackage> packages = new ArrayList<>();
        for (int i = 0; i < r.parcels().size(); i++) {
            String tracking = "NXT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            String text = (r.isReturn() ? "TEST RETURN LABEL" : "TEST LABEL") + " " + tracking + " to " + r.to().postalCode();
            packages.add(new Shipping.LabelPackage(tracking, Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)), "TXT"));
        }
        BigDecimal cost = rates(account, new Shipping.RateRequest(r.from(), r.to(), r.parcels())).get(0).amount();
        return new Shipping.Label("NXS-" + packages.get(0).trackingNumber(), packages.get(0).trackingNumber(), packages, cost, "USD");
    }

    @Override
    public boolean voidLabel(Account account, String carrierShipmentId, String trackingNumber) {
        return true;
    }

    @Override
    public Shipping.AddressCheck validate(Account account, Shipping.Address a) {
        List<String> problems = new ArrayList<>();
        if (a.lines() == null || a.lines().isEmpty() || a.lines().get(0).isBlank()) problems.add("Street address is missing");
        if (a.postalCode() == null || a.postalCode().isBlank()) problems.add("Postal code is missing");
        if (a.countryCode() == null || a.countryCode().length() != 2) problems.add("Country must be a two-letter code");
        return new Shipping.AddressCheck(problems.isEmpty(), false, null, null, problems);
    }

    @Override
    public Shipping.Tracking track(Account account, String trackingNumber) {
        // the last digit of the hash decides, so a test number always tells the same story
        int step = Math.abs(trackingNumber.hashCode()) % 4;
        String[] status = {"LABEL_CREATED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"};
        return new Shipping.Tracking(trackingNumber, status[step], "TEST_" + status[step], null,
                List.of(new Shipping.TrackEvent(null, status[step], "Test event", "Test city")));
    }

    private static void check(Shipping.Address a) {
        if (a == null || a.postalCode() == null || a.postalCode().isBlank()) throw new CarrierException(400, "The destination has no postal code");
    }

    static BigDecimal weight(List<Shipping.Parcel> parcels) {
        BigDecimal kg = BigDecimal.ZERO;
        for (Shipping.Parcel p : parcels == null ? List.<Shipping.Parcel>of() : parcels) kg = kg.add(p.weightKg() == null ? BigDecimal.ONE : p.weightKg());
        return kg;
    }
}
