package com.nexus.gateway.carrier;

import com.nexus.gateway.account.Accounts;
import com.nexus.gateway.log.RequestLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The single shipping API: picks the client's account for the store and carrier, calls the carrier, keeps every
 * label so it can be printed again, and logs every call with its timing.
 */
@Service
public class ShippingService {

    private final Map<String, CarrierAdapter> carriers = new LinkedHashMap<>();
    private final Accounts accounts;
    private final RequestLog log;
    private final JdbcTemplate jdbc;

    public ShippingService(List<CarrierAdapter> adapters, Accounts accounts, RequestLog log, JdbcTemplate jdbc) {
        adapters.forEach(a -> carriers.put(a.code(), a));
        this.accounts = accounts;
        this.log = log;
        this.jdbc = jdbc;
    }

    public List<String> carriers() {
        return List.copyOf(carriers.keySet());
    }

    /** Rates from one carrier, or from every carrier the client has an account for when carrier is null; cheapest first. */
    public List<Shipping.Quote> rates(String clientId, String storeId, String carrier, Shipping.RateRequest r) {
        List<Shipping.Quote> all = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String c : carrier == null ? carriers.keySet() : List.of(carrier.toUpperCase())) {
            CarrierAdapter adapter = adapter(c);
            Accounts.Account acct;
            try {
                acct = accounts.resolve(clientId, storeId, c);
            } catch (IllegalStateException e) {
                if (carrier != null) throw e;
                continue;   // no account for this carrier: skip it when shopping all
            }
            try {
                all.addAll(log.timed(clientId, storeId, "RATES", c, null, () -> adapter.rates(acct, r)));
            } catch (RuntimeException e) {
                if (carrier != null) throw e;
                errors.add(c + ": " + e.getMessage());
            }
        }
        if (all.isEmpty() && !errors.isEmpty()) throw new CarrierException(502, String.join("; ", errors));
        all.sort(java.util.Comparator.comparing(Shipping.Quote::amount));
        return all;
    }

    public record SavedLabel(String labelId, String trackingNumber, String format) {
    }

    public record LabelResult(String carrier, String carrierShipmentId, String masterTrackingNumber, List<SavedLabel> labels,
                              java.math.BigDecimal cost, String currency) {
    }

    public LabelResult label(String clientId, String storeId, String carrier, Shipping.LabelRequest r) {
        String c = carrier.toUpperCase();
        CarrierAdapter adapter = adapter(c);
        Accounts.Account acct = accounts.resolve(clientId, storeId, c);
        Shipping.Label label = log.timed(clientId, storeId, r.isReturn() ? "RETURN_LABEL" : "LABEL", c, r.reference(), () -> adapter.label(acct, r));
        List<SavedLabel> saved = new ArrayList<>();
        for (Shipping.LabelPackage p : label.packages()) {
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO label_document (label_id, client_id, store_id, carrier, tracking_number, carrier_shipment_id, image_format, content_base64) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, clientId, storeId, c, p.trackingNumber(), label.carrierShipmentId(), p.format(),
                    p.labelBase64() == null ? "" : p.labelBase64());
            saved.add(new SavedLabel(id, p.trackingNumber(), p.format()));
        }
        return new LabelResult(c, label.carrierShipmentId(), label.masterTrackingNumber(), saved, label.cost(), label.currency());
    }

    public record Document(String format, String contentBase64, boolean voided) {
    }

    /** A label the client made before, to print again. */
    public Optional<Document> document(String clientId, String labelId) {
        return jdbc.queryForList("SELECT image_format, content_base64, voided FROM label_document WHERE label_id = ? AND client_id = ?", labelId, clientId)
                .stream().findFirst().map(r -> new Document((String) r.get("image_format"), (String) r.get("content_base64"), Boolean.TRUE.equals(r.get("voided"))));
    }

    /** Voids a label by its tracking number; the carrier shipment id is looked up from the saved label. */
    public boolean voidLabel(String clientId, String storeId, String carrier, String trackingNumber) {
        String c = carrier.toUpperCase();
        List<String> ids = jdbc.queryForList("SELECT carrier_shipment_id FROM label_document WHERE client_id = ? AND carrier = ? AND tracking_number = ?",
                String.class, clientId, c, trackingNumber);
        String shipmentId = ids.isEmpty() ? trackingNumber : ids.get(0);
        Accounts.Account acct = accounts.resolve(clientId, storeId, c);
        boolean ok = log.timed(clientId, storeId, "VOID", c, trackingNumber, () -> adapter(c).voidLabel(acct, shipmentId, trackingNumber));
        if (ok) jdbc.update("UPDATE label_document SET voided = TRUE WHERE client_id = ? AND carrier = ? AND carrier_shipment_id = ?", clientId, c, shipmentId);
        return ok;
    }

    public Shipping.AddressCheck validate(String clientId, String storeId, String carrier, Shipping.Address address) {
        String c = carrier.toUpperCase();
        Accounts.Account acct = accounts.resolve(clientId, storeId, c);
        return log.timed(clientId, storeId, "ADDRESS", c, address.postalCode(), () -> adapter(c).validate(acct, address));
    }

    public Shipping.Tracking track(String clientId, String storeId, String carrier, String trackingNumber) {
        String c = carrier.toUpperCase();
        Accounts.Account acct = accounts.resolve(clientId, storeId, c);
        return log.timed(clientId, storeId, "TRACK", c, trackingNumber, () -> adapter(c).track(acct, trackingNumber));
    }

    private CarrierAdapter adapter(String code) {
        CarrierAdapter a = carriers.get(code);
        if (a == null) throw new IllegalArgumentException("Unknown carrier " + code + "; the gateway has " + carriers.keySet());
        return a;
    }
}
