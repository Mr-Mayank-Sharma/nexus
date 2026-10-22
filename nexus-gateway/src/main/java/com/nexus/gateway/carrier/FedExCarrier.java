package com.nexus.gateway.carrier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.gateway.account.Accounts.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FedEx REST APIs (OAuth client credentials; Ship, Rate, Track, Address Validation). Account settings:
 * {@code accountNumber}; secrets: {@code clientId}, {@code clientSecret}. {@code baseUrl} overrides the host (tests).
 * Written from FedEx's published API; check it against the FedEx sandbox before production use.
 */
@Component
public class FedExCarrier implements CarrierAdapter {

    private static final String SANDBOX = "https://apis-sandbox.fedex.com";
    private static final String PRODUCTION = "https://apis.fedex.com";
    static final String DEFAULT_SERVICE = "FEDEX_GROUND";
    /** FedEx needs a phone number for every party; this placeholder is used when none is known. */
    static final String NO_PHONE = "0000000000";

    private final OAuthTokenCache tokenCache = new OAuthTokenCache();

    @Override
    public String code() {
        return "FEDEX";
    }

    @Override
    public List<Shipping.Quote> rates(Account account, Shipping.RateRequest request) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.putObject("accountNumber").put("value", account.setting("accountNumber"));
        ObjectNode shipment = body.putObject("requestedShipment");
        shipment.putObject("shipper").set("address", address(request.from()));
        shipment.putObject("recipient").set("address", address(request.to()));
        shipment.put("pickupType", "DROPOFF_AT_FEDEX_LOCATION");
        shipment.putArray("rateRequestType").add("ACCOUNT").add("LIST");
        shipment.set("requestedPackageLineItems", packages(request.parcels()));

        JsonNode response = call(account, "POST", "/rate/v1/rates/quotes", body);
        List<Shipping.Quote> quotes = new ArrayList<>();
        for (JsonNode rateReply : response.at("/output/rateReplyDetails")) {
            JsonNode charge = rateReply.at("/ratedShipmentDetails/0");
            quotes.add(new Shipping.Quote(code(), rateReply.path("serviceType").asText(), rateReply.path("serviceName").asText(null),
                    new BigDecimal(charge.path("totalNetCharge").asText("0")), charge.path("currency").asText("USD"), null));
        }
        return quotes;
    }

    @Override
    public Shipping.Label label(Account account, Shipping.LabelRequest request) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.put("labelResponseOptions", "LABEL");
        body.putObject("accountNumber").put("value", account.setting("accountNumber"));
        ObjectNode shipment = body.putObject("requestedShipment");
        shipment.set("shipper", party(request.from()));
        shipment.putArray("recipients").add(party(request.to()));
        shipment.put("shipDatestamp", LocalDate.now(ZoneOffset.UTC).toString());
        shipment.put("serviceType", request.service() == null ? DEFAULT_SERVICE : request.service());
        shipment.put("packagingType", "YOUR_PACKAGING");
        shipment.put("pickupType", "USE_SCHEDULED_PICKUP");
        shipment.putObject("shippingChargesPayment").put("paymentType", "SENDER");
        ObjectNode labelSpecification = shipment.putObject("labelSpecification");
        String imageType = request.imageFormat() == null ? "PDF" : request.imageFormat();
        labelSpecification.put("imageType", imageType);
        labelSpecification.put("labelStockType", "PAPER_4X6");
        if (request.isReturn()) {
            ObjectNode specialServices = shipment.putObject("shipmentSpecialServices");
            specialServices.putArray("specialServiceTypes").add("RETURN_SHIPMENT");
            specialServices.putObject("returnShipmentDetail").put("returnType", "PRINT_RETURN_LABEL");
        }
        ArrayNode packageItems = packages(request.parcels());
        if (request.reference() != null) {
            packageItems.forEach(packageItem -> ((ObjectNode) packageItem).putArray("customerReferences").addObject()
                    .put("customerReferenceType", "CUSTOMER_REFERENCE")
                    .put("value", request.reference()));
        }
        shipment.set("requestedPackageLineItems", packageItems);

        JsonNode transaction = call(account, "POST", "/ship/v1/shipments", body).at("/output/transactionShipments/0");
        List<Shipping.LabelPackage> labelPackages = new ArrayList<>();
        for (JsonNode piece : transaction.path("pieceResponses")) {
            JsonNode document = piece.at("/packageDocuments/0");
            labelPackages.add(new Shipping.LabelPackage(piece.path("trackingNumber").asText(), document.path("encodedLabel").asText(null),
                    document.path("docType").asText(imageType)));
        }
        if (labelPackages.isEmpty()) {
            throw new CarrierException(502, "FedEx returned no label");
        }
        String masterTrackingNumber = transaction.path("masterTrackingNumber").asText(labelPackages.get(0).trackingNumber());
        JsonNode netCharge = transaction.at("/completedShipmentDetail/shipmentRating/shipmentRateDetails/0/totalNetCharge");
        BigDecimal cost = netCharge.isMissingNode() ? null : new BigDecimal(netCharge.asText());
        return new Shipping.Label(masterTrackingNumber, masterTrackingNumber, labelPackages, cost, "USD");
    }

    @Override
    public boolean voidLabel(Account account, String carrierShipmentId, String trackingNumber) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.putObject("accountNumber").put("value", account.setting("accountNumber"));
        body.put("trackingNumber", trackingNumber);
        body.put("deletionControl", "DELETE_ALL_PACKAGES");
        return call(account, "PUT", "/ship/v1/shipments/cancel", body).at("/output/cancelledShipment").asBoolean(false);
    }

    @Override
    public Shipping.AddressCheck validate(Account account, Shipping.Address address) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.putArray("addressesToValidate").addObject().set("address", address(address));
        JsonNode resolved = call(account, "POST", "/address/v1/addresses/resolve", body).at("/output/resolvedAddresses/0");

        List<String> messages = new ArrayList<>();
        resolved.path("customerMessages").forEach(message -> messages.add(message.path("message").asText(message.asText())));
        boolean wasResolved = resolved.at("/attributes/Resolved").asText("false").equalsIgnoreCase("true");
        boolean ambiguous = resolved.at("/attributes/MultipleMatches").asText("false").equalsIgnoreCase("true");
        String classification = resolved.path("classification").asText("UNKNOWN");
        boolean residential = "RESIDENTIAL".equals(classification);

        List<String> resolvedLines = new ArrayList<>();
        resolved.path("streetLinesToken").forEach(line -> resolvedLines.add(line.asText()));
        Shipping.Address suggested = new Shipping.Address(address.name(), address.company(), address.phone(), address.email(),
                resolvedLines.isEmpty() ? address.lines() : resolvedLines, resolved.path("city").asText(address.city()),
                resolved.path("stateOrProvinceCode").asText(address.state()), resolved.path("postalCode").asText(address.postalCode()),
                resolved.path("countryCode").asText(address.countryCode()), residential);
        Boolean knownResidential = "UNKNOWN".equals(classification) ? null : residential;
        return new Shipping.AddressCheck(wasResolved && !ambiguous, ambiguous, suggested, knownResidential, messages);
    }

    @Override
    public Shipping.Tracking track(Account account, String trackingNumber) {
        ObjectNode body = HttpJson.JSON.createObjectNode();
        body.put("includeDetailedScans", true);
        body.putArray("trackingInfo").addObject().putObject("trackingNumberInfo").put("trackingNumber", trackingNumber);
        JsonNode trackResult = call(account, "POST", "/track/v1/trackingnumbers", body).at("/output/completeTrackResults/0/trackResults/0");

        String statusCode = trackResult.at("/latestStatusDetail/code").asText("");
        List<Shipping.TrackEvent> events = new ArrayList<>();
        for (JsonNode scan : trackResult.path("scanEvents")) {
            events.add(new Shipping.TrackEvent(scan.path("date").asText(null), scan.path("eventType").asText(null), scan.path("eventDescription").asText(null),
                    scan.at("/scanLocation/city").asText(null)));
        }
        String estimatedDelivery = null;
        for (JsonNode dateAndTime : trackResult.path("dateAndTimes")) {
            if ("ESTIMATED_DELIVERY".equals(dateAndTime.path("type").asText())) {
                estimatedDelivery = dateAndTime.path("dateTime").asText();
            }
        }
        String description = trackResult.at("/latestStatusDetail/description").asText(statusCode);
        return new Shipping.Tracking(trackingNumber, status(statusCode), description, estimatedDelivery, events);
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

    // ── calling FedEx ──

    private JsonNode call(Account account, String method, String path, Object body) {
        return HttpJson.send(method, baseUrl(account) + path, Map.of("Authorization", "Bearer " + accessToken(account), "X-locale", "en_US"), body);
    }

    private String accessToken(Account account) {
        return tokenCache.tokenFor(account.accountId(), () -> {
            JsonNode response = HttpJson.form(baseUrl(account) + "/oauth/token", Map.of(), Map.of("grant_type", "client_credentials",
                    "client_id", account.secret("clientId"), "client_secret", account.secret("clientSecret")));
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

    // ── building FedEx JSON ──

    static ObjectNode address(Shipping.Address address) {
        ObjectNode fedexAddress = HttpJson.JSON.createObjectNode();
        ArrayNode streetLines = fedexAddress.putArray("streetLines");
        if (address.lines() != null) {
            address.lines().forEach(streetLines::add);
        }
        fedexAddress.put("city", address.city());
        fedexAddress.put("stateOrProvinceCode", address.state());
        fedexAddress.put("postalCode", address.postalCode());
        fedexAddress.put("countryCode", address.countryCode());
        if (address.residential() != null) {
            fedexAddress.put("residential", address.residential());
        }
        return fedexAddress;
    }

    /** A shipper or recipient: contact (name, company, phone, email) and address. */
    static ObjectNode party(Shipping.Address address) {
        ObjectNode fedexParty = HttpJson.JSON.createObjectNode();
        ObjectNode contact = fedexParty.putObject("contact");
        contact.put("personName", address.name());
        if (address.company() != null) {
            contact.put("companyName", address.company());
        }
        contact.put("phoneNumber", address.phone() == null ? NO_PHONE : address.phone());
        if (address.email() != null) {
            contact.put("emailAddress", address.email());
        }
        fedexParty.set("address", address(address));
        return fedexParty;
    }

    /** FedEx package lines in kilograms and whole centimetres. */
    static ArrayNode packages(List<Shipping.Parcel> parcels) {
        ArrayNode packageLines = HttpJson.JSON.createArrayNode();
        for (Shipping.Parcel parcel : parcels) {
            ObjectNode packageLine = packageLines.addObject();
            packageLine.putObject("weight").put("units", "KG").put("value", parcel.weightKg() == null ? BigDecimal.ONE : parcel.weightKg());
            boolean hasDimensions = parcel.lengthCm() != null && parcel.widthCm() != null && parcel.heightCm() != null;
            if (hasDimensions) {
                packageLine.putObject("dimensions")
                        .put("length", parcel.lengthCm().intValue())
                        .put("width", parcel.widthCm().intValue())
                        .put("height", parcel.heightCm().intValue())
                        .put("units", "CM");
            }
        }
        return packageLines;
    }
}
