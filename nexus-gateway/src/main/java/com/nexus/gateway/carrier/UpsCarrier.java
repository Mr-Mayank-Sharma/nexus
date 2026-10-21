package com.nexus.gateway.carrier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.gateway.account.Accounts.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UPS REST APIs (OAuth client credentials; Rating, Shipping, Tracking, Address Validation). Account settings:
 * {@code accountNumber}; secrets: {@code clientId}, {@code clientSecret}. {@code baseUrl} overrides the host (tests).
 * Written from UPS's published API; check it against the UPS customer integration environment before production use.
 */
@Component
public class UpsCarrier implements CarrierAdapter {

    private static final String SANDBOX = "https://wwwcie.ups.com";
    private static final String PRODUCTION = "https://onlinetools.ups.com";
    private static final String VERSION = "v2409";
    private final Map<String, String[]> tokens = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return "UPS";
    }

    @Override
    public List<Shipping.Quote> rates(Account a, Shipping.RateRequest r) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        ObjectNode req = body.putObject("RateRequest");
        req.putObject("Request").put("RequestOption", "Shop");
        ObjectNode s = req.putObject("Shipment");
        ObjectNode shipper = s.putObject("Shipper");
        shipper.put("ShipperNumber", a.setting("accountNumber"));
        shipper.set("Address", address(r.from()));
        s.putObject("ShipTo").set("Address", address(r.to()));
        s.putObject("ShipFrom").set("Address", address(r.from()));
        s.set("Package", packages(r.parcels(), "PackagingType"));
        JsonNode out = call(a, "POST", "/api/rating/" + VERSION + "/Shop", body);
        List<Shipping.Quote> quotes = new ArrayList<>();
        for (JsonNode q : list(out.at("/RateResponse/RatedShipment"))) {
            String service = q.at("/Service/Code").asText();
            quotes.add(new Shipping.Quote(code(), service, serviceName(service), new BigDecimal(q.at("/TotalCharges/MonetaryValue").asText("0")),
                    q.at("/TotalCharges/CurrencyCode").asText("USD"), q.at("/GuaranteedDelivery/BusinessDaysInTransit").isMissingNode() ? null
                    : q.at("/GuaranteedDelivery/BusinessDaysInTransit").asInt()));
        }
        return quotes;
    }

    @Override
    public Shipping.Label label(Account a, Shipping.LabelRequest r) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        ObjectNode req = body.putObject("ShipmentRequest");
        req.putObject("Request").put("RequestOption", "nonvalidate");
        ObjectNode s = req.putObject("Shipment");
        s.put("Description", r.reference() == null ? "Order" : r.reference());
        ObjectNode shipper = party(r.from());
        shipper.put("ShipperNumber", a.setting("accountNumber"));
        s.set("Shipper", shipper);
        s.set("ShipTo", party(r.to()));
        s.set("ShipFrom", party(r.from()));
        s.putObject("PaymentInformation").putArray("ShipmentCharge").addObject().put("Type", "01")
                .putObject("BillShipper").put("AccountNumber", a.setting("accountNumber"));
        s.putObject("Service").put("Code", r.service() == null ? "03" : r.service());
        if (r.isReturn()) s.putObject("ReturnService").put("Code", "9");
        s.set("Package", packages(r.parcels(), "Packaging"));
        req.putObject("LabelSpecification").putObject("LabelImageFormat").put("Code", r.imageFormat() == null ? "GIF" : r.imageFormat());
        JsonNode res = call(a, "POST", "/api/shipments/" + VERSION + "/ship", body).at("/ShipmentResponse/ShipmentResults");
        List<Shipping.LabelPackage> pkgs = new ArrayList<>();
        for (JsonNode p : list(res.path("PackageResults"))) {
            pkgs.add(new Shipping.LabelPackage(p.path("TrackingNumber").asText(), p.at("/ShippingLabel/GraphicImage").asText(null),
                    p.at("/ShippingLabel/ImageFormat/Code").asText("GIF")));
        }
        if (pkgs.isEmpty()) throw new CarrierException(502, "UPS returned no label");
        JsonNode charge = res.at("/ShipmentCharges/TotalCharges");
        return new Shipping.Label(res.path("ShipmentIdentificationNumber").asText(), pkgs.get(0).trackingNumber(), pkgs,
                charge.isMissingNode() ? null : new BigDecimal(charge.path("MonetaryValue").asText("0")), charge.path("CurrencyCode").asText("USD"));
    }

    @Override
    public boolean voidLabel(Account a, String carrierShipmentId, String trackingNumber) {
        JsonNode r = call(a, "DELETE", "/api/shipments/" + VERSION + "/void/cancel/" + carrierShipmentId, null);
        return "1".equals(r.at("/VoidShipmentResponse/SummaryResult/Status/Code").asText());
    }

    @Override
    public Shipping.AddressCheck validate(Account a, Shipping.Address address) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        ObjectNode k = body.putObject("XAVRequest").putObject("AddressKeyFormat");
        ArrayNode lines = k.putArray("AddressLine");
        if (address.lines() != null) address.lines().forEach(lines::add);
        k.put("PoliticalDivision2", address.city());
        k.put("PoliticalDivision1", address.state());
        k.put("PostcodePrimaryLow", address.postalCode());
        k.put("CountryCode", address.countryCode());
        JsonNode r = call(a, "POST", "/api/addressvalidation/v2/3", body).path("XAVResponse");
        boolean valid = r.has("ValidAddressIndicator");
        boolean ambiguous = r.has("AmbiguousAddressIndicator");
        Shipping.Address suggested = null;
        Boolean residential = null;
        JsonNode c = list(r.path("Candidate")).stream().findFirst().orElse(null);
        if (c != null) {
            JsonNode f = c.path("AddressKeyFormat");
            List<String> l = new ArrayList<>();
            list(f.path("AddressLine")).forEach(x -> l.add(x.asText()));
            suggested = new Shipping.Address(address.name(), address.company(), address.phone(), address.email(), l, f.path("PoliticalDivision2").asText(null),
                    f.path("PoliticalDivision1").asText(null), f.path("PostcodePrimaryLow").asText(null), f.path("CountryCode").asText(null), null);
            String cls = c.at("/AddressClassification/Code").asText("0");
            residential = "2".equals(cls) ? Boolean.TRUE : "1".equals(cls) ? Boolean.FALSE : null;
        }
        List<String> messages = new ArrayList<>();
        if (r.has("NoCandidatesIndicator")) messages.add("UPS found no matching address");
        if (ambiguous) messages.add("UPS found more than one possible address");
        return new Shipping.AddressCheck(valid, ambiguous, suggested, residential, messages);
    }

    @Override
    public Shipping.Tracking track(Account a, String trackingNumber) {
        JsonNode p = call(a, "GET", "/api/track/v1/details/" + trackingNumber + "?locale=en_US", null).at("/trackResponse/shipment/0/package/0");
        String type = p.at("/currentStatus/type").asText(p.at("/activity/0/status/type").asText(""));
        List<Shipping.TrackEvent> events = new ArrayList<>();
        for (JsonNode e : p.path("activity")) {
            events.add(new Shipping.TrackEvent(e.path("date").asText("") + " " + e.path("time").asText(""), e.at("/status/code").asText(null),
                    e.at("/status/description").asText(null), e.at("/location/address/city").asText(null)));
        }
        String eta = null;
        for (JsonNode d : p.path("deliveryDate")) if ("DEL".equals(d.path("type").asText()) || "SDD".equals(d.path("type").asText())) eta = d.path("date").asText();
        return new Shipping.Tracking(trackingNumber, status(type), p.at("/currentStatus/description").asText(type), eta, events);
    }

    /** UPS status types: M manifest, P pickup, I in transit, O out for delivery, D delivered, X exception. */
    static String status(String type) {
        return switch (type) {
            case "M", "MV" -> "LABEL_CREATED";
            case "P", "I" -> "IN_TRANSIT";
            case "O" -> "OUT_FOR_DELIVERY";
            case "D" -> "DELIVERED";
            case "X", "RS" -> "EXCEPTION";
            default -> "UNKNOWN";
        };
    }

    static String serviceName(String code) {
        return switch (code) {
            case "01" -> "UPS Next Day Air";
            case "02" -> "UPS 2nd Day Air";
            case "03" -> "UPS Ground";
            case "12" -> "UPS 3 Day Select";
            case "13" -> "UPS Next Day Air Saver";
            case "14" -> "UPS Next Day Air Early";
            case "59" -> "UPS 2nd Day Air A.M.";
            case "65" -> "UPS Saver";
            default -> "UPS service " + code;
        };
    }

    // ── helpers ──

    private JsonNode call(Account a, String method, String path, Object body) {
        return HttpJson.send(method, base(a) + path, Map.of("Authorization", "Bearer " + token(a), "transId", UUID.randomUUID().toString().substring(0, 32),
                "transactionSrc", "nexus-gateway"), body);
    }

    private String token(Account a) {
        String[] t = tokens.get(a.accountId());
        if (t != null && Long.parseLong(t[1]) > Instant.now().getEpochSecond() + 60) return t[0];
        String basic = Base64.getEncoder().encodeToString((a.secret("clientId") + ":" + a.secret("clientSecret")).getBytes(StandardCharsets.UTF_8));
        JsonNode r = HttpJson.form(base(a) + "/security/v1/oauth/token", Map.of("Authorization", "Basic " + basic, "x-merchant-id", a.setting("accountNumber")),
                Map.of("grant_type", "client_credentials"));
        String token = r.path("access_token").asText();
        tokens.put(a.accountId(), new String[]{token, String.valueOf(Instant.now().getEpochSecond() + r.path("expires_in").asLong(3600))});
        return token;
    }

    private static String base(Account a) {
        String override = a.setting("baseUrl");
        return override != null ? override : a.sandbox() ? SANDBOX : PRODUCTION;
    }

    /** UPS gives a single object where there is one and an array where there are more. */
    static List<JsonNode> list(JsonNode n) {
        List<JsonNode> out = new ArrayList<>();
        if (n == null || n.isMissingNode() || n.isNull()) return out;
        if (n.isArray()) n.forEach(out::add);
        else out.add(n);
        return out;
    }

    static ObjectNode address(Shipping.Address a) {
        ObjectNode n = HttpJson.JSON.createObjectNode();
        ArrayNode lines = n.putArray("AddressLine");
        if (a.lines() != null) a.lines().forEach(lines::add);
        n.put("City", a.city());
        n.put("StateProvinceCode", a.state());
        n.put("PostalCode", a.postalCode());
        n.put("CountryCode", a.countryCode());
        if (Boolean.TRUE.equals(a.residential())) n.put("ResidentialAddressIndicator", "");
        return n;
    }

    static ObjectNode party(Shipping.Address a) {
        ObjectNode n = HttpJson.JSON.createObjectNode();
        n.put("Name", a.company() != null ? a.company() : a.name());
        if (a.name() != null) n.put("AttentionName", a.name());
        if (a.phone() != null) n.putObject("Phone").put("Number", a.phone());
        n.set("Address", address(a));
        return n;
    }

    static ArrayNode packages(List<Shipping.Parcel> parcels, String packagingField) {
        ArrayNode items = HttpJson.JSON.createArrayNode();
        for (Shipping.Parcel p : parcels) {
            ObjectNode i = items.addObject();
            i.putObject(packagingField).put("Code", "02");
            ObjectNode w = i.putObject("PackageWeight");
            w.putObject("UnitOfMeasurement").put("Code", "KGS");
            w.put("Weight", (p.weightKg() == null ? BigDecimal.ONE : p.weightKg()).toPlainString());
            if (p.lengthCm() != null && p.widthCm() != null && p.heightCm() != null) {
                ObjectNode d = i.putObject("Dimensions");
                d.putObject("UnitOfMeasurement").put("Code", "CM");
                d.put("Length", p.lengthCm().toPlainString()).put("Width", p.widthCm().toPlainString()).put("Height", p.heightCm().toPlainString());
            }
        }
        return items;
    }
}
