package com.nexus.oms.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.CoreStatus;
import com.nexus.core.model.basic.Geo;
import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.party.Party;
import com.nexus.core.model.party.PostalAddress;
import com.nexus.core.model.shipment.Shipment;
import com.nexus.core.model.shipment.ShipmentPackage;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ProductStoreService;
import com.nexus.core.service.ShipmentService;
import com.nexus.oms.integration.core.CredentialVault;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The OMS side of the Nexus Gateway (doc 26, W11). Each product store keeps only the gateway's address, its API key
 * (encrypted with the OMS credential vault) and its default carrier; carrier and email credentials live in the
 * gateway. Labels are made for a core shipment from its boxes and addresses; tracking and a reprint link are stored
 * on each box.
 */
@Service
public class GatewayService {

    static final String URL_SETTING = "GATEWAY_URL";
    static final String KEY_SETTING = "GATEWAY_API_KEY";
    static final String CARRIER_SETTING = "GATEWAY_CARRIER";
    static final String KEY_HEADER = "X-Nexus-Gateway-Key";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final EntityManager entityManager;
    private final ProductStoreService productStoreService;
    private final OrderService orderService;
    private final ShipmentService shipmentService;
    private final CredentialVault credentialVault;

    public GatewayService(EntityManager entityManager, ProductStoreService productStoreService, OrderService orderService, ShipmentService shipmentService,
                          CredentialVault credentialVault) {
        this.entityManager = entityManager;
        this.productStoreService = productStoreService;
        this.orderService = orderService;
        this.shipmentService = shipmentService;
        this.credentialVault = credentialVault;
    }

    public record LabelOutcome(String shipmentId, String carrier, List<ShipmentService.PackageLabel> labels, BigDecimal cost, String currency) {
    }

    /** A store's gateway: its address and its (decrypted) key. */
    record Gateway(String url, String key) {
    }

    /** Points a store at a gateway. The key is kept encrypted; a null key keeps the one already set. */
    @Transactional
    public void configure(String productStoreId, String url, String apiKey, String defaultCarrier) {
        boolean secureOrLocal = url != null && (url.startsWith("https://") || url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1"));
        if (!secureOrLocal) {
            throw new IllegalArgumentException("The gateway address must use https (http is allowed only for localhost)");
        }
        productStoreService.setSetting(productStoreId, URL_SETTING, url.replaceAll("/+$", ""));
        if (apiKey != null && !apiKey.isBlank()) {
            productStoreService.setSetting(productStoreId, KEY_SETTING, credentialVault.encrypt(apiKey.trim()));
        }
        if (defaultCarrier != null) {
            productStoreService.setSetting(productStoreId, CARRIER_SETTING, defaultCarrier.trim().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Makes carrier labels for a shipment through its store's gateway: one per box (one box with everything when it has
     * none), from the shipping facility's address to the shipment's destination. Tracking and reprint links are stored.
     */
    @Transactional
    public LabelOutcome label(String shipmentId, String carrier, String service, String userId) {
        // Step 1: the shipment, its order's store, and that store's gateway and carrier.
        Shipment shipment = shipmentService.get(shipmentId);
        if (shipment.getPrimaryOrderId() == null) {
            throw new IllegalStateException("Shipment " + shipmentId + " has no order; labels here are for order shipments");
        }
        OrderHeader order = orderService.header(shipment.getPrimaryOrderId());
        String productStoreId = order.getProductStoreId();
        if (productStoreId == null) {
            throw new IllegalStateException("Order " + order.getOrderId() + " has no store, so no gateway is set up for it");
        }
        Gateway gateway = gatewayOf(productStoreId);
        String carrierCode = carrier != null
                ? carrier.toUpperCase(Locale.ROOT)
                : productStoreService.setting(productStoreId, CARRIER_SETTING)
                        .orElseThrow(() -> new IllegalStateException("Name a carrier or set the store's GATEWAY_CARRIER"));

        // Step 2: the boxes (packing first makes one box when there are none).
        if (!CoreStatus.Shipment.PACKED.equals(shipment.getStatusId())) {
            shipmentService.pack(shipmentId, userId);
        }
        List<ShipmentPackage> boxes = shipmentService.packages(shipmentId);
        List<Map<String, Object>> parcels = new ArrayList<>();
        for (ShipmentPackage box : boxes) {
            parcels.add(Map.of("weightKg", box.getWeight() == null ? BigDecimal.ONE : box.getWeight()));
        }

        // Step 3: ask the gateway.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("storeId", productStoreId);
        request.put("carrier", carrierCode);
        request.put("service", service);
        request.put("from", facilityAddress(shipment.getOriginFacilityId()));
        request.put("to", addressOf(shipment.getDestinationContactMechId(), null));
        request.put("parcels", parcels);
        request.put("reference", "Order " + order.getOrderId());
        JsonNode response = post(gateway, "/v1/labels", request);

        // Step 4: one label per box, in order, stored on the shipment.
        List<ShipmentService.PackageLabel> labels = new ArrayList<>();
        JsonNode returnedLabels = response.path("labels");
        for (int boxIndex = 0; boxIndex < boxes.size() && boxIndex < returnedLabels.size(); boxIndex++) {
            JsonNode returnedLabel = returnedLabels.get(boxIndex);
            labels.add(new ShipmentService.PackageLabel(boxes.get(boxIndex).getShipmentPackageSeqId(), returnedLabel.path("trackingNumber").asText(),
                    gateway.url() + "/v1/labels/" + returnedLabel.path("labelId").asText()));
        }
        BigDecimal cost = response.hasNonNull("cost") ? new BigDecimal(response.get("cost").asText()) : null;
        String currency = response.path("currency").asText(null);
        OrderItemShipGroup shipGroup = orderService.shipGroupOrNull(shipment.getPrimaryOrderId(), shipment.getPrimaryShipGroupSeqId());
        String shipmentMethod = shipGroup == null ? null : shipGroup.getShipmentMethodTypeId();
        shipmentService.recordLabels(shipmentId, carrierPartyId(carrierCode), shipmentMethod, labels, cost, currency, userId);
        return new LabelOutcome(shipmentId, carrierCode, labels, cost, currency);
    }

    /** Sends a test email through the store's gateway with the template given (the gateway must have it). Returns its message id. */
    public String testEmail(String productStoreId, String templateId, String to) {
        Map<String, Object> data = Map.of("storeName", productStoreId, "message", "This is a test from Nexus");
        return email(productStoreId, templateId == null ? "TEST" : templateId, to, data);
    }

    /** Sends a customer email (pickup ready, cancellation, completion) through the store's gateway. Returns its message id. */
    public String email(String productStoreId, String templateId, String to, Map<String, Object> data) {
        Map<String, Object> request = Map.of("storeId", productStoreId, "templateId", templateId, "to", to, "data", data);
        return post(gatewayOf(productStoreId), "/v1/email/send", request).path("messageId").asText();
    }

    // ── internals ──

    private Gateway gatewayOf(String productStoreId) {
        String url = productStoreService.setting(productStoreId, URL_SETTING)
                .orElseThrow(() -> new IllegalStateException("Store " + productStoreId + " has no gateway set up"));
        String encryptedKey = productStoreService.setting(productStoreId, KEY_SETTING)
                .orElseThrow(() -> new IllegalStateException("Store " + productStoreId + " has no gateway key"));
        return new Gateway(url, credentialVault.decrypt(encryptedKey));
    }

    /** Posts JSON to the gateway; a reply of 300 or above is an error carrying the gateway's own message. */
    private JsonNode post(Gateway gateway, String path, Object requestBody) {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(gateway.url() + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(KEY_HEADER, gateway.key())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(requestBody)))
                    .build();
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not reach the gateway at " + gateway.url() + ": interrupted");
        } catch (Exception unreachable) {
            throw new IllegalStateException("Could not reach the gateway at " + gateway.url() + ": " + unreachable.getMessage());
        }

        JsonNode body;
        try {
            body = response.body() == null || response.body().isBlank() ? jsonMapper.createObjectNode() : jsonMapper.readTree(response.body());
        } catch (Exception notJson) {
            throw new IllegalStateException("Could not reach the gateway at " + gateway.url() + ": " + notJson.getMessage());
        }
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("The gateway said: " + body.path("error").asText("HTTP " + response.statusCode()));
        }
        return body;
    }

    /** Carriers are seeded as parties with their code as id (UPS, FEDEX); the test carrier has none. */
    private String carrierPartyId(String carrierCode) {
        return entityManager.find(Party.class, carrierCode) != null ? carrierCode : null;
    }

    /** The facility's postal address; just its name and postal code when it has no full address yet. */
    private Map<String, Object> facilityAddress(String facilityId) {
        Facility facility = entityManager.find(Facility.class, facilityId);
        List<String> contactMechIds = entityManager.createQuery("SELECT c.contactMechId FROM FacilityContactMech c WHERE c.facilityId = :facilityId "
                        + "AND c.thruDate IS NULL ORDER BY c.fromDate", String.class)
                .setParameter("facilityId", facilityId)
                .getResultList();
        for (String contactMechId : contactMechIds) {
            if (entityManager.find(PostalAddress.class, contactMechId) != null) {
                return addressOf(contactMechId, facility.getFacilityName());
            }
        }
        Map<String, Object> partialAddress = new LinkedHashMap<>();
        partialAddress.put("name", facility.getFacilityName());
        partialAddress.put("postalCode", facility.getPostalCode());
        return partialAddress;
    }

    /** A postal address in the gateway's shape; {@code name} overrides the address's own "to" name. Empty when unknown. */
    private Map<String, Object> addressOf(String contactMechId, String name) {
        Map<String, Object> gatewayAddress = new LinkedHashMap<>();
        if (contactMechId == null) {
            return gatewayAddress;
        }
        PostalAddress address = entityManager.find(PostalAddress.class, contactMechId);
        if (address == null) {
            return gatewayAddress;
        }
        gatewayAddress.put("name", name != null ? name : address.getToName());
        List<String> lines = new ArrayList<>();
        if (address.getAddress1() != null) {
            lines.add(address.getAddress1());
        }
        if (address.getAddress2() != null) {
            lines.add(address.getAddress2());
        }
        gatewayAddress.put("lines", lines);
        gatewayAddress.put("city", address.getCity());
        gatewayAddress.put("postalCode", address.getPostalCode());
        if (address.getStateProvinceGeoId() != null) {
            gatewayAddress.put("state", stateCode(address.getStateProvinceGeoId()));
        }
        if (address.getCountryGeoId() != null) {
            Geo country = entityManager.find(Geo.class, address.getCountryGeoId());
            boolean hasAlpha2 = country != null && country.getGeoCodeAlpha2() != null;
            gatewayAddress.put("countryCode", hasAlpha2 ? country.getGeoCodeAlpha2() : address.getCountryGeoId());
        }
        if (address.getIsResidential() != null) {
            gatewayAddress.put("residential", address.getIsResidential());
        }
        return gatewayAddress;
    }

    /** A state's short code: its abbreviation, else the part of the geo id after the dash ("USA-NJ" is "NJ"). */
    private String stateCode(String stateGeoId) {
        Geo state = entityManager.find(Geo.class, stateGeoId);
        if (state != null && state.getAbbreviation() != null) {
            return state.getAbbreviation();
        }
        int dash = stateGeoId.indexOf('-');
        return dash >= 0 ? stateGeoId.substring(dash + 1) : stateGeoId;
    }
}
