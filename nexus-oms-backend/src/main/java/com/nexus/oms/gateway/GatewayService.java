package com.nexus.oms.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.model.basic.Geo;
import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.party.PostalAddress;
import com.nexus.core.model.shipment.Shipment;
import com.nexus.core.model.shipment.ShipmentPackage;
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
import java.util.Map;

/**
 * The OMS side of the Nexus Gateway (doc 26, W11). Each product store keeps only the gateway's address, its API key
 * (encrypted with the OMS credential vault) and its default carrier; carrier and email credentials live in the
 * gateway. Labels are made for a core shipment from its boxes and addresses; tracking and a reprint link are stored
 * on each box.
 */
@Service
public class GatewayService {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    private final EntityManager em;
    private final ProductStoreService stores;
    private final ShipmentService shipments;
    private final CredentialVault vault;

    public GatewayService(EntityManager em, ProductStoreService stores, ShipmentService shipments, CredentialVault vault) {
        this.em = em;
        this.stores = stores;
        this.shipments = shipments;
        this.vault = vault;
    }

    /** Points a store at a gateway. The key is kept encrypted; a null key keeps the one already set. */
    @Transactional
    public void configure(String productStoreId, String url, String apiKey, String defaultCarrier) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1"))) {
            throw new IllegalArgumentException("The gateway address must use https (http is allowed only for localhost)");
        }
        stores.setSetting(productStoreId, "GATEWAY_URL", url.replaceAll("/+$", ""));
        if (apiKey != null && !apiKey.isBlank()) stores.setSetting(productStoreId, "GATEWAY_API_KEY", vault.encrypt(apiKey.trim()));
        if (defaultCarrier != null) stores.setSetting(productStoreId, "GATEWAY_CARRIER", defaultCarrier.trim().toUpperCase());
    }

    public record LabelOutcome(String shipmentId, String carrier, List<ShipmentService.PackageLabel> labels, BigDecimal cost, String currency) {
    }

    /**
     * Makes carrier labels for a shipment through its store's gateway: one per box (one box with everything when it has
     * none), from the shipping facility's address to the shipment's destination. Tracking and reprint links are stored.
     */
    @Transactional
    public LabelOutcome label(String shipmentId, String carrier, String service, String userId) {
        Shipment s = shipments.get(shipmentId);
        if (s.getPrimaryOrderId() == null) throw new IllegalStateException("Shipment " + shipmentId + " has no order; labels here are for order shipments");
        OrderHeader order = em.find(OrderHeader.class, s.getPrimaryOrderId());
        String store = order.getProductStoreId();
        if (store == null) throw new IllegalStateException("Order " + order.getOrderId() + " has no store, so no gateway is set up for it");
        Gateway g = gateway(store);
        String c = carrier != null ? carrier.toUpperCase() : stores.setting(store, "GATEWAY_CARRIER").orElseThrow(
                () -> new IllegalStateException("Name a carrier or set the store's GATEWAY_CARRIER"));
        if (!"SHIPMENT_PACKED".equals(s.getStatusId())) shipments.pack(shipmentId, userId);
        List<ShipmentPackage> boxes = shipments.packages(shipmentId);
        List<Map<String, Object>> parcels = new ArrayList<>();
        for (ShipmentPackage b : boxes) parcels.add(Map.of("weightKg", b.getWeight() == null ? BigDecimal.ONE : b.getWeight()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", store);
        body.put("carrier", c);
        body.put("service", service);
        body.put("from", from(s.getOriginFacilityId()));
        body.put("to", address(s.getDestinationContactMechId(), null));
        body.put("parcels", parcels);
        body.put("reference", "Order " + order.getOrderId());
        JsonNode r = post(g, "/v1/labels", body);
        List<ShipmentService.PackageLabel> labels = new ArrayList<>();
        JsonNode got = r.path("labels");
        for (int i = 0; i < boxes.size() && i < got.size(); i++) {
            labels.add(new ShipmentService.PackageLabel(boxes.get(i).getShipmentPackageSeqId(), got.get(i).path("trackingNumber").asText(),
                    g.url() + "/v1/labels/" + got.get(i).path("labelId").asText()));
        }
        BigDecimal cost = r.hasNonNull("cost") ? new BigDecimal(r.get("cost").asText()) : null;
        var group = em.find(com.nexus.core.model.order.OrderItemShipGroup.class,
                new com.nexus.core.model.order.OrderItemShipGroup.Key(s.getPrimaryOrderId(), s.getPrimaryShipGroupSeqId()));
        shipments.recordLabels(shipmentId, carrierParty(c), group == null ? null : group.getShipmentMethodTypeId(), labels, cost,
                r.path("currency").asText(null), userId);
        return new LabelOutcome(shipmentId, c, labels, cost, r.path("currency").asText(null));
    }

    /** Sends a test email through the store's gateway with the template given (the gateway must have it). */
    public String testEmail(String productStoreId, String templateId, String to) {
        Gateway g = gateway(productStoreId);
        return post(g, "/v1/email/send", Map.of("storeId", productStoreId, "templateId", templateId == null ? "TEST" : templateId, "to", to,
                "data", Map.of("storeName", productStoreId, "message", "This is a test from Nexus"))).path("messageId").asText();
    }

    /** Sends a customer email (pickup ready, cancellation, completion) through the store's gateway. */
    public String email(String productStoreId, String templateId, String to, Map<String, Object> data) {
        return post(gateway(productStoreId), "/v1/email/send", Map.of("storeId", productStoreId, "templateId", templateId, "to", to, "data", data))
                .path("messageId").asText();
    }

    // ── internals ──

    record Gateway(String url, String key) {
    }

    private Gateway gateway(String productStoreId) {
        String url = stores.setting(productStoreId, "GATEWAY_URL").orElseThrow(() -> new IllegalStateException("Store " + productStoreId + " has no gateway set up"));
        String sealed = stores.setting(productStoreId, "GATEWAY_API_KEY").orElseThrow(() -> new IllegalStateException("Store " + productStoreId + " has no gateway key"));
        return new Gateway(url, vault.decrypt(sealed));
    }

    private JsonNode post(Gateway g, String path, Object body) {
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(g.url() + path)).timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json").header("X-Nexus-Gateway-Key", g.key())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode n = r.body() == null || r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
            if (r.statusCode() >= 300) throw new IllegalStateException("The gateway said: " + n.path("error").asText("HTTP " + r.statusCode()));
            return n;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not reach the gateway at " + g.url() + ": " + e.getMessage());
        }
    }

    /** Carriers are seeded as parties with their code as id (UPS, FEDEX); the test carrier has none. */
    private String carrierParty(String carrier) {
        return em.find(com.nexus.core.model.party.Party.class, carrier) != null ? carrier : null;
    }

    private Map<String, Object> from(String facilityId) {
        Facility f = em.find(Facility.class, facilityId);
        List<String> mechs = em.createQuery("SELECT c.contactMechId FROM FacilityContactMech c WHERE c.facilityId = :f AND c.thruDate IS NULL ORDER BY c.fromDate",
                String.class).setParameter("f", facilityId).getResultList();
        for (String m : mechs) {
            if (em.find(PostalAddress.class, m) != null) return address(m, f.getFacilityName());
        }
        // a facility without a full address yet: its name and postal code are what we have
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", f.getFacilityName());
        a.put("postalCode", f.getPostalCode());
        return a;
    }

    private Map<String, Object> address(String contactMechId, String name) {
        Map<String, Object> a = new LinkedHashMap<>();
        if (contactMechId == null) return a;
        PostalAddress p = em.find(PostalAddress.class, contactMechId);
        if (p == null) return a;
        a.put("name", name != null ? name : p.getToName());
        List<String> lines = new ArrayList<>();
        if (p.getAddress1() != null) lines.add(p.getAddress1());
        if (p.getAddress2() != null) lines.add(p.getAddress2());
        a.put("lines", lines);
        a.put("city", p.getCity());
        a.put("postalCode", p.getPostalCode());
        if (p.getStateProvinceGeoId() != null) {
            Geo st = em.find(Geo.class, p.getStateProvinceGeoId());
            a.put("state", st != null && st.getAbbreviation() != null ? st.getAbbreviation()
                    : p.getStateProvinceGeoId().contains("-") ? p.getStateProvinceGeoId().substring(p.getStateProvinceGeoId().indexOf('-') + 1) : p.getStateProvinceGeoId());
        }
        if (p.getCountryGeoId() != null) {
            Geo c = em.find(Geo.class, p.getCountryGeoId());
            a.put("countryCode", c != null && c.getGeoCodeAlpha2() != null ? c.getGeoCodeAlpha2() : p.getCountryGeoId());
        }
        if (p.getIsResidential() != null) a.put("residential", p.getIsResidential());
        return a;
    }
}
