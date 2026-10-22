package com.nexus.gateway.carrier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.gateway.account.Accounts.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UPS REST APIs (OAuth client credentials; Rating, Shipping, Tracking, Address Validation). Account settings:
 * {@code accountNumber}; secrets: {@code clientId}, {@code clientSecret}. {@code baseUrl} overrides the host (tests).
 * Written from UPS's published API; check it against the UPS customer integration environment before production use.
 */
@Component
public class UpsCarrier implements CarrierAdapter {

    private static final String SANDBOX = "https://wwwcie.ups.com";
    private static final String PRODUCTION = "https://onlinetools.ups.com";
    private static final String API_VERSION = "v2409";
    static final String GROUND = "03";
    static final String CUSTOMER_PACKAGING = "02";
    static final String BILL_SHIPPER = "01";
    static final String PRINT_RETURN_LABEL = "9";
    private static final int MAX_TRANSACTION_ID_LENGTH = 32;

    private final OAuthTokenCache tokenCache = new OAuthTokenCache();

    @Override
    public String code() {
        return "UPS";
    }

    @Override
    public List<Shipping.Quote> rates(Account account, Shipping.RateRequest request) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        ObjectNode rateRequest = body.putObject("RateRequest");
        rateRequest.putObject("Request").put("RequestOption", "Shop");   // every service, not one
        ObjectNode shipment = rateRequest.putObject("Shipment");
        ObjectNode shipper = shipment.putObject("Shipper");
        shipper.put("ShipperNumber", account.setting("accountNumber"));
        shipper.set("Address", address(request.from()));
        shipment.putObject("ShipTo").set("Address", address(request.to()));
        shipment.putObject("ShipFrom").set("Address", address(request.from()));
        shipment.set("Package", packages(request.parcels(), "PackagingType"));

        JsonNode response = call(account, "POST", "/api/rating/" + API_VERSION + "/Shop", body);
        List<Shipping.Quote> quotes = new ArrayList<>();
        for (JsonNode ratedShipment : asList(response.at("/RateResponse/RatedShipment"))) {
            String serviceCode = ratedShipment.at("/Service/Code").asText();
            BigDecimal price = new BigDecimal(ratedShipment.at("/TotalCharges/MonetaryValue").asText("0"));
            String currency = ratedShipment.at("/TotalCharges/CurrencyCode").asText("USD");
            JsonNode transitDays = ratedShipment.at("/GuaranteedDelivery/BusinessDaysInTransit");
            quotes.add(new Shipping.Quote(code(), serviceCode, serviceName(serviceCode), price, currency, transitDays.isMissingNode() ? null : transitDays.asInt()));
        }
        return quotes;
    }

    @Override
    public Shipping.Label label(Account account, Shipping.LabelRequest request) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        ObjectNode shipmentRequest = body.putObject("ShipmentRequest");
        shipmentRequest.putObject("Request").put("RequestOption", "nonvalidate");
        ObjectNode shipment = shipmentRequest.putObject("Shipment");
        shipment.put("Description", request.reference() == null ? "Order" : request.reference());
        ObjectNode shipper = party(request.from());
        shipper.put("ShipperNumber", account.setting("accountNumber"));
        shipment.set("Shipper", shipper);
        shipment.set("ShipTo", party(request.to()));
        shipment.set("ShipFrom", party(request.from()));
        shipment.putObject("PaymentInformation").putArray("ShipmentCharge").addObject().put("Type", BILL_SHIPPER)
                .putObject("BillShipper").put("AccountNumber", account.setting("accountNumber"));
        shipment.putObject("Service").put("Code", request.service() == null ? GROUND : request.service());
        if (request.isReturn()) {
            shipment.putObject("ReturnService").put("Code", PRINT_RETURN_LABEL);
        }
        shipment.set("Package", packages(request.parcels(), "Packaging"));
        String imageFormat = request.imageFormat() == null ? "GIF" : request.imageFormat();
        shipmentRequest.putObject("LabelSpecification").putObject("LabelImageFormat").put("Code", imageFormat);

        JsonNode results = call(account, "POST", "/api/shipments/" + API_VERSION + "/ship", body).at("/ShipmentResponse/ShipmentResults");
        List<Shipping.LabelPackage> labelPackages = new ArrayList<>();
        for (JsonNode packageResult : asList(results.path("PackageResults"))) {
            labelPackages.add(new Shipping.LabelPackage(packageResult.path("TrackingNumber").asText(), packageResult.at("/ShippingLabel/GraphicImage").asText(null),
                    packageResult.at("/ShippingLabel/ImageFormat/Code").asText("GIF")));
        }
        if (labelPackages.isEmpty()) {
            throw new CarrierException(502, "UPS returned no label");
        }
        JsonNode totalCharges = results.at("/ShipmentCharges/TotalCharges");
        BigDecimal cost = totalCharges.isMissingNode() ? null : new BigDecimal(totalCharges.path("MonetaryValue").asText("0"));
        return new Shipping.Label(results.path("ShipmentIdentificationNumber").asText(), labelPackages.get(0).trackingNumber(), labelPackages, cost,
                totalCharges.path("CurrencyCode").asText("USD"));
    }

    @Override
    public boolean voidLabel(Account account, String carrierShipmentId, String trackingNumber) {
        JsonNode response = call(account, "DELETE", "/api/shipments/" + API_VERSION + "/void/cancel/" + carrierShipmentId, null);
        return "1".equals(response.at("/VoidShipmentResponse/SummaryResult/Status/Code").asText());
    }

    @Override
    public Shipping.AddressCheck validate(Account account, Shipping.Address address) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        ObjectNode keyFormat = body.putObject("XAVRequest").putObject("AddressKeyFormat");
        ArrayNode lines = keyFormat.putArray("AddressLine");
        if (address.lines() != null) {
            address.lines().forEach(lines::add);
        }
        keyFormat.put("PoliticalDivision2", address.city());
        keyFormat.put("PoliticalDivision1", address.state());
        keyFormat.put("PostcodePrimaryLow", address.postalCode());
        keyFormat.put("CountryCode", address.countryCode());

        JsonNode response = call(account, "POST", "/api/addressvalidation/v2/3", body).path("XAVResponse");
        boolean valid = response.has("ValidAddressIndicator");
        boolean ambiguous = response.has("AmbiguousAddressIndicator");
        Shipping.Address suggested = null;
        Boolean residential = null;
        JsonNode firstCandidate = asList(response.path("Candidate")).stream().findFirst().orElse(null);
        if (firstCandidate != null) {
            JsonNode candidateAddress = firstCandidate.path("AddressKeyFormat");
            List<String> candidateLines = new ArrayList<>();
            asList(candidateAddress.path("AddressLine")).forEach(line -> candidateLines.add(line.asText()));
            suggested = new Shipping.Address(address.name(), address.company(), address.phone(), address.email(), candidateLines,
                    candidateAddress.path("PoliticalDivision2").asText(null), candidateAddress.path("PoliticalDivision1").asText(null),
                    candidateAddress.path("PostcodePrimaryLow").asText(null), candidateAddress.path("CountryCode").asText(null), null);
            residential = residentialFromClassification(firstCandidate.at("/AddressClassification/Code").asText("0"));
        }
        List<String> messages = new ArrayList<>();
        if (response.has("NoCandidatesIndicator")) {
            messages.add("UPS found no matching address");
        }
        if (ambiguous) {
            messages.add("UPS found more than one possible address");
        }
        return new Shipping.AddressCheck(valid, ambiguous, suggested, residential, messages);
    }

    @Override
    public Shipping.Tracking track(Account account, String trackingNumber) {
        JsonNode trackedPackage = call(account, "GET", "/api/track/v1/details/" + trackingNumber + "?locale=en_US", null)
                .at("/trackResponse/shipment/0/package/0");
        String statusType = trackedPackage.at("/currentStatus/type").asText(trackedPackage.at("/activity/0/status/type").asText(""));
        List<Shipping.TrackEvent> events = new ArrayList<>();
        for (JsonNode activity : trackedPackage.path("activity")) {
            String when = activity.path("date").asText("") + " " + activity.path("time").asText("");
            events.add(new Shipping.TrackEvent(when, activity.at("/status/code").asText(null), activity.at("/status/description").asText(null),
                    activity.at("/location/address/city").asText(null)));
        }
        String estimatedDelivery = null;
        for (JsonNode deliveryDate : trackedPackage.path("deliveryDate")) {
            String type = deliveryDate.path("type").asText();
            if ("DEL".equals(type) || "SDD".equals(type)) {
                estimatedDelivery = deliveryDate.path("date").asText();
            }
        }
        String description = trackedPackage.at("/currentStatus/description").asText(statusType);
        return new Shipping.Tracking(trackingNumber, status(statusType), description, estimatedDelivery, events);
    }

    /** UPS status types: M manifest, P pickup, I in transit, O out for delivery, D delivered, X exception. */
    static String status(String upsStatusType) {
        return switch (upsStatusType) {
            case "M", "MV" -> "LABEL_CREATED";
            case "P", "I" -> "IN_TRANSIT";
            case "O" -> "OUT_FOR_DELIVERY";
            case "D" -> "DELIVERED";
            case "X", "RS" -> "EXCEPTION";
            default -> "UNKNOWN";
        };
    }

    static String serviceName(String serviceCode) {
        return switch (serviceCode) {
            case "01" -> "UPS Next Day Air";
            case "02" -> "UPS 2nd Day Air";
            case "03" -> "UPS Ground";
            case "12" -> "UPS 3 Day Select";
            case "13" -> "UPS Next Day Air Saver";
            case "14" -> "UPS Next Day Air Early";
            case "59" -> "UPS 2nd Day Air A.M.";
            case "65" -> "UPS Saver";
            default -> "UPS service " + serviceCode;
        };
    }

    /** UPS address classification: 2 residential, 1 commercial, anything else unknown. */
    private static Boolean residentialFromClassification(String classificationCode) {
        if ("2".equals(classificationCode)) {
            return Boolean.TRUE;
        }
        return "1".equals(classificationCode) ? Boolean.FALSE : null;
    }

    // ── calling UPS ──

    private JsonNode call(Account account, String method, String path, Object body) {
        String transactionId = UUID.randomUUID().toString().substring(0, MAX_TRANSACTION_ID_LENGTH);
        Map<String, String> headers = Map.of("Authorization", "Bearer " + accessToken(account), "transId", transactionId, "transactionSrc", "nexus-gateway");
        return HttpJson.send(method, baseUrl(account) + path, headers, body);
    }

    private String accessToken(Account account) {
        return tokenCache.tokenFor(account.accountId(), () -> {
            String credentials = account.secret("clientId") + ":" + account.secret("clientSecret");
            String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            JsonNode response = HttpJson.form(baseUrl(account) + "/security/v1/oauth/token",
                    Map.of("Authorization", "Basic " + basicAuth, "x-merchant-id", account.setting("accountNumber")),
                    Map.of("grant_type", "client_credentials"));
            return new OAuthTokenCache.IssuedToken(response.path("access_token").asText(),
                    response.path("expires_in").asLong(OAuthTokenCache.DEFAULT_LIFETIME_SECONDS));
        });
    }

    private static String baseUrl(Account account) {
        String override = account.setting("baseUrl");
        if (override != null) {
            return override;
        }
        return account.sandbox() ? SANDBOX : PRODUCTION;
    }

    // ── building UPS JSON ──

    /** UPS gives a single object where there is one and an array where there are more. */
    static List<JsonNode> asList(JsonNode node) {
        List<JsonNode> nodes = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return nodes;
        }
        if (node.isArray()) {
            node.forEach(nodes::add);
        } else {
            nodes.add(node);
        }
        return nodes;
    }

    static ObjectNode address(Shipping.Address address) {
        ObjectNode upsAddress = HttpJson.JSON.createObjectNode();
        ArrayNode lines = upsAddress.putArray("AddressLine");
        if (address.lines() != null) {
            address.lines().forEach(lines::add);
        }
        upsAddress.put("City", address.city());
        upsAddress.put("StateProvinceCode", address.state());
        upsAddress.put("PostalCode", address.postalCode());
        upsAddress.put("CountryCode", address.countryCode());
        if (Boolean.TRUE.equals(address.residential())) {
            upsAddress.put("ResidentialAddressIndicator", "");
        }
        return upsAddress;
    }

    /** A shipper or recipient: company name (or person), attention name, phone, address. */
    static ObjectNode party(Shipping.Address address) {
        ObjectNode upsParty = HttpJson.JSON.createObjectNode();
        upsParty.put("Name", address.company() != null ? address.company() : address.name());
        if (address.name() != null) {
            upsParty.put("AttentionName", address.name());
        }
        if (address.phone() != null) {
            upsParty.putObject("Phone").put("Number", address.phone());
        }
        upsParty.set("Address", address(address));
        return upsParty;
    }

    /** UPS packages in kilograms and centimetres; rating and shipping name the packaging field differently. */
    static ArrayNode packages(List<Shipping.Parcel> parcels, String packagingField) {
        ArrayNode upsPackages = HttpJson.JSON.createArrayNode();
        for (Shipping.Parcel parcel : parcels) {
            ObjectNode upsPackage = upsPackages.addObject();
            upsPackage.putObject(packagingField).put("Code", CUSTOMER_PACKAGING);
            ObjectNode weight = upsPackage.putObject("PackageWeight");
            weight.putObject("UnitOfMeasurement").put("Code", "KGS");
            weight.put("Weight", (parcel.weightKg() == null ? BigDecimal.ONE : parcel.weightKg()).toPlainString());
            boolean hasDimensions = parcel.lengthCm() != null && parcel.widthCm() != null && parcel.heightCm() != null;
            if (hasDimensions) {
                ObjectNode dimensions = upsPackage.putObject("Dimensions");
                dimensions.putObject("UnitOfMeasurement").put("Code", "CM");
                dimensions.put("Length", parcel.lengthCm().toPlainString())
                        .put("Width", parcel.widthCm().toPlainString())
                        .put("Height", parcel.heightCm().toPlainString());
            }
        }
        return upsPackages;
    }
}
