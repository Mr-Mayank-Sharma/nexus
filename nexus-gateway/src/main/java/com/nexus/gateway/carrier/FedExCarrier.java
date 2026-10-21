package com.nexus.gateway.carrier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.gateway.account.Accounts.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FedEx REST APIs (OAuth client credentials; Ship, Rate, Track, Address Validation). Account settings:
 * {@code accountNumber}; secrets: {@code clientId}, {@code clientSecret}. {@code baseUrl} overrides the host (tests).
 * Written from FedEx's published API; check it against the FedEx sandbox before production use.
 */
@Component
public class FedExCarrier implements CarrierAdapter {

    private static final String SANDBOX = "https://apis-sandbox.fedex.com";
    private static final String PRODUCTION = "https://apis.fedex.com";
    private final Map<String, String[]> tokens = new ConcurrentHashMap<>();   // accountId -> {token, expiresAtEpochSecond}

    @Override
    public String code() {
        return "FEDEX";
    }

    @Override
    public List<Shipping.Quote> rates(Account a, Shipping.RateRequest r) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.putObject("accountNumber").put("value", a.setting("accountNumber"));
        ObjectNode s = body.putObject("requestedShipment");
        s.putObject("shipper").set("address", address(r.from()));
        s.putObject("recipient").set("address", address(r.to()));
        s.put("pickupType", "DROPOFF_AT_FEDEX_LOCATION");
        s.putArray("rateRequestType").add("ACCOUNT").add("LIST");
        s.set("requestedPackageLineItems", packages(r.parcels()));
        JsonNode out = call(a, "POST", "/rate/v1/rates/quotes", body);
        List<Shipping.Quote> quotes = new ArrayList<>();
        for (JsonNode d : out.at("/output/rateReplyDetails")) {
            JsonNode charge = d.at("/ratedShipmentDetails/0");
            quotes.add(new Shipping.Quote(code(), d.path("serviceType").asText(), d.path("serviceName").asText(null),
                    new BigDecimal(charge.path("totalNetCharge").asText("0")), charge.path("currency").asText("USD"), null));
        }
        return quotes;
    }

    @Override
    public Shipping.Label label(Account a, Shipping.LabelRequest r) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.put("labelResponseOptions", "LABEL");
        body.putObject("accountNumber").put("value", a.setting("accountNumber"));
        ObjectNode s = body.putObject("requestedShipment");
        s.set("shipper", party(r.from()));
        s.putArray("recipients").add(party(r.to()));
        s.put("shipDatestamp", LocalDate.now(ZoneOffset.UTC).toString());
        s.put("serviceType", r.service() == null ? "FEDEX_GROUND" : r.service());
        s.put("packagingType", "YOUR_PACKAGING");
        s.put("pickupType", "USE_SCHEDULED_PICKUP");
        s.putObject("shippingChargesPayment").put("paymentType", "SENDER");
        ObjectNode spec = s.putObject("labelSpecification");
        spec.put("imageType", r.imageFormat() == null ? "PDF" : r.imageFormat());
        spec.put("labelStockType", "PAPER_4X6");
        if (r.isReturn()) {
            ObjectNode special = s.putObject("shipmentSpecialServices");
            special.putArray("specialServiceTypes").add("RETURN_SHIPMENT");
            special.putObject("returnShipmentDetail").put("returnType", "PRINT_RETURN_LABEL");
        }
        ArrayNode items = packages(r.parcels());
        if (r.reference() != null) items.forEach(p -> ((ObjectNode) p).putArray("customerReferences").addObject().put("customerReferenceType", "CUSTOMER_REFERENCE").put("value", r.reference()));
        s.set("requestedPackageLineItems", items);
        JsonNode t = call(a, "POST", "/ship/v1/shipments", body).at("/output/transactionShipments/0");
        List<Shipping.LabelPackage> pkgs = new ArrayList<>();
        for (JsonNode p : t.path("pieceResponses")) {
            JsonNode doc = p.at("/packageDocuments/0");
            pkgs.add(new Shipping.LabelPackage(p.path("trackingNumber").asText(), doc.path("encodedLabel").asText(null), doc.path("docType").asText(spec.path("imageType").asText())));
        }
        if (pkgs.isEmpty()) throw new CarrierException(502, "FedEx returned no label");
        JsonNode charge = t.at("/completedShipmentDetail/shipmentRating/shipmentRateDetails/0/totalNetCharge");
        return new Shipping.Label(t.path("masterTrackingNumber").asText(pkgs.get(0).trackingNumber()), t.path("masterTrackingNumber").asText(pkgs.get(0).trackingNumber()),
                pkgs, charge.isMissingNode() ? null : new BigDecimal(charge.asText()), "USD");
    }

    @Override
    public boolean voidLabel(Account a, String carrierShipmentId, String trackingNumber) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.putObject("accountNumber").put("value", a.setting("accountNumber"));
        body.put("trackingNumber", trackingNumber);
        body.put("deletionControl", "DELETE_ALL_PACKAGES");
        return call(a, "PUT", "/ship/v1/shipments/cancel", body).at("/output/cancelledShipment").asBoolean(false);
    }

    @Override
    public Shipping.AddressCheck validate(Account a, Shipping.Address address) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.putArray("addressesToValidate").addObject().set("address", address(address));
        JsonNode r = call(a, "POST", "/address/v1/addresses/resolve", body).at("/output/resolvedAddresses/0");
        List<String> messages = new ArrayList<>();
        r.path("customerMessages").forEach(m -> messages.add(m.path("message").asText(m.asText())));
        boolean resolved = r.at("/attributes/Resolved").asText("false").equalsIgnoreCase("true");
        boolean ambiguous = r.at("/attributes/MultipleMatches").asText("false").equalsIgnoreCase("true");
        String classification = r.path("classification").asText("UNKNOWN");
        List<String> lines = new ArrayList<>();
        r.path("streetLinesToken").forEach(l -> lines.add(l.asText()));
        Shipping.Address suggested = new Shipping.Address(address.name(), address.company(), address.phone(), address.email(),
                lines.isEmpty() ? address.lines() : lines, r.path("city").asText(address.city()), r.path("stateOrProvinceCode").asText(address.state()),
                r.path("postalCode").asText(address.postalCode()), r.path("countryCode").asText(address.countryCode()), "RESIDENTIAL".equals(classification));
        return new Shipping.AddressCheck(resolved && !ambiguous, ambiguous, suggested, "UNKNOWN".equals(classification) ? null : "RESIDENTIAL".equals(classification), messages);
    }

    @Override
    public Shipping.Tracking track(Account a, String trackingNumber) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.put("includeDetailedScans", true);
        body.putArray("trackingInfo").addObject().putObject("trackingNumberInfo").put("trackingNumber", trackingNumber);
        JsonNode t = call(a, "POST", "/track/v1/trackingnumbers", body).at("/output/completeTrackResults/0/trackResults/0");
        String code = t.at("/latestStatusDetail/code").asText("");
        List<Shipping.TrackEvent> events = new ArrayList<>();
        for (JsonNode e : t.path("scanEvents")) {
            events.add(new Shipping.TrackEvent(e.path("date").asText(null), e.path("eventType").asText(null), e.path("eventDescription").asText(null),
                    e.at("/scanLocation/city").asText(null)));
        }
        String eta = null;
        for (JsonNode d : t.path("dateAndTimes")) if ("ESTIMATED_DELIVERY".equals(d.path("type").asText())) eta = d.path("dateTime").asText();
        return new Shipping.Tracking(trackingNumber, status(code), t.at("/latestStatusDetail/description").asText(code), eta, events);
    }

    static String status(String fedexCode) {
        return switch (fedexCode) {
            case "OC", "LP" -> "LABEL_CREATED";
            case "PU", "IT", "AR", "DP", "AF", "CC", "OF" -> "IN_TRANSIT";
            case "OD" -> "OUT_FOR_DELIVERY";
            case "DL" -> "DELIVERED";
            case "DE", "SE", "CA", "RS" -> "EXCEPTION";
            default -> "UNKNOWN";
        };
    }

    // ── helpers ──

    private JsonNode call(Account a, String method, String path, Object body) {
        return HttpJson.send(method, base(a) + path, Map.of("Authorization", "Bearer " + token(a), "X-locale", "en_US"), body);
    }

    private String token(Account a) {
        String[] t = tokens.get(a.accountId());
        if (t != null && Long.parseLong(t[1]) > Instant.now().getEpochSecond() + 60) return t[0];
        JsonNode r = HttpJson.form(base(a) + "/oauth/token", Map.of(), Map.of("grant_type", "client_credentials",
                "client_id", a.secret("clientId"), "client_secret", a.secret("clientSecret")));
        String token = r.path("access_token").asText();
        tokens.put(a.accountId(), new String[]{token, String.valueOf(Instant.now().getEpochSecond() + r.path("expires_in").asLong(3600))});
        return token;
    }

    private static String base(Account a) {
        String override = a.setting("baseUrl");
        return override != null ? override : a.sandbox() ? SANDBOX : PRODUCTION;
    }

    static ObjectNode address(Shipping.Address a) {
        ObjectNode n = HttpJson.JSON.createObjectNode();
        ArrayNode lines = n.putArray("streetLines");
        if (a.lines() != null) a.lines().forEach(lines::add);
        n.put("city", a.city());
        n.put("stateOrProvinceCode", a.state());
        n.put("postalCode", a.postalCode());
        n.put("countryCode", a.countryCode());
        if (a.residential() != null) n.put("residential", a.residential());
        return n;
    }

    static ObjectNode party(Shipping.Address a) {
        ObjectNode n = HttpJson.JSON.createObjectNode();
        ObjectNode c = n.putObject("contact");
        c.put("personName", a.name());
        if (a.company() != null) c.put("companyName", a.company());
        c.put("phoneNumber", a.phone() == null ? "0000000000" : a.phone());
        if (a.email() != null) c.put("emailAddress", a.email());
        n.set("address", address(a));
        return n;
    }

    static ArrayNode packages(List<Shipping.Parcel> parcels) {
        ArrayNode items = HttpJson.JSON.createArrayNode();
        for (Shipping.Parcel p : parcels) {
            ObjectNode i = items.addObject();
            i.putObject("weight").put("units", "KG").put("value", p.weightKg() == null ? BigDecimal.ONE : p.weightKg());
            if (p.lengthCm() != null && p.widthCm() != null && p.heightCm() != null) {
                i.putObject("dimensions").put("length", p.lengthCm().intValue()).put("width", p.widthCm().intValue())
                        .put("height", p.heightCm().intValue()).put("units", "CM");
            }
        }
        return items;
    }
}
