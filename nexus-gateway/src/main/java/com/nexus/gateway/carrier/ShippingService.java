package com.nexus.gateway.carrier;

import com.nexus.gateway.account.Accounts;
import com.nexus.gateway.log.RequestLog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The single shipping API: picks the client's account for the store and carrier, calls the carrier, keeps every
 * label so it can be printed again ({@link LabelDocuments}), and logs every call with its timing ({@link RequestLog}).
 */
@Service
public class ShippingService {

    private final Map<String, CarrierAdapter> carrierByCode = new LinkedHashMap<>();
    private final Accounts accounts;
    private final RequestLog requestLog;
    private final LabelDocuments labelDocuments;

    public ShippingService(List<CarrierAdapter> carrierAdapters, Accounts accounts, RequestLog requestLog, LabelDocuments labelDocuments) {
        carrierAdapters.forEach(adapter -> carrierByCode.put(adapter.code(), adapter));
        this.accounts = accounts;
        this.requestLog = requestLog;
        this.labelDocuments = labelDocuments;
    }

    public record SavedLabel(String labelId, String trackingNumber, String format) {
    }

    public record LabelResult(String carrier, String carrierShipmentId, String masterTrackingNumber, List<SavedLabel> labels, BigDecimal cost,
                              String currency) {
    }

    /** The carriers the gateway can talk to. */
    public List<String> carriers() {
        return List.copyOf(carrierByCode.keySet());
    }

    /**
     * Rates from one carrier, or from every carrier the client has an account for when {@code carrier} is null;
     * cheapest first. When shopping all carriers, one that has no account or fails is skipped; only when none answers
     * is it an error.
     */
    public List<Shipping.Quote> rates(String clientId, String storeId, String carrier, Shipping.RateRequest request) {
        boolean shoppingAll = carrier == null;
        List<String> carrierCodes = shoppingAll ? List.copyOf(carrierByCode.keySet()) : List.of(carrier.toUpperCase(Locale.ROOT));
        List<Shipping.Quote> quotes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String carrierCode : carrierCodes) {
            CarrierAdapter adapter = adapterFor(carrierCode);
            Accounts.Account account;
            try {
                account = accounts.resolve(clientId, storeId, carrierCode);
            } catch (IllegalStateException noAccount) {
                if (!shoppingAll) {
                    throw noAccount;
                }
                continue;
            }
            try {
                quotes.addAll(requestLog.timed(clientId, storeId, "RATES", carrierCode, null, () -> adapter.rates(account, request)));
            } catch (RuntimeException failure) {
                if (!shoppingAll) {
                    throw failure;
                }
                failures.add(carrierCode + ": " + failure.getMessage());
            }
        }
        if (quotes.isEmpty() && !failures.isEmpty()) {
            throw new CarrierException(502, String.join("; ", failures));
        }
        quotes.sort(Comparator.comparing(Shipping.Quote::amount));
        return quotes;
    }

    /** Makes a label (or a return label) and saves each box's label so it can be printed again. */
    public LabelResult label(String clientId, String storeId, String carrier, Shipping.LabelRequest request) {
        String carrierCode = carrier.toUpperCase(Locale.ROOT);
        CarrierAdapter adapter = adapterFor(carrierCode);
        Accounts.Account account = accounts.resolve(clientId, storeId, carrierCode);
        String operation = request.isReturn() ? "RETURN_LABEL" : "LABEL";
        Shipping.Label label = requestLog.timed(clientId, storeId, operation, carrierCode, request.reference(), () -> adapter.label(account, request));

        List<SavedLabel> savedLabels = new ArrayList<>();
        for (Shipping.LabelPackage labelPackage : label.packages()) {
            String labelId = labelDocuments.save(clientId, storeId, carrierCode, label.carrierShipmentId(), labelPackage);
            savedLabels.add(new SavedLabel(labelId, labelPackage.trackingNumber(), labelPackage.format()));
        }
        return new LabelResult(carrierCode, label.carrierShipmentId(), label.masterTrackingNumber(), savedLabels, label.cost(), label.currency());
    }

    /** A label the client made before, to print again. */
    public Optional<LabelDocuments.Document> document(String clientId, String labelId) {
        return labelDocuments.find(clientId, labelId);
    }

    /** Voids a label by its tracking number; the carrier shipment id is looked up from the saved label. */
    public boolean voidLabel(String clientId, String storeId, String carrier, String trackingNumber) {
        String carrierCode = carrier.toUpperCase(Locale.ROOT);
        String carrierShipmentId = labelDocuments.carrierShipmentIdOf(clientId, carrierCode, trackingNumber).orElse(trackingNumber);
        Accounts.Account account = accounts.resolve(clientId, storeId, carrierCode);
        boolean voided = requestLog.timed(clientId, storeId, "VOID", carrierCode, trackingNumber,
                () -> adapterFor(carrierCode).voidLabel(account, carrierShipmentId, trackingNumber));
        if (voided) {
            labelDocuments.markVoided(clientId, carrierCode, carrierShipmentId);
        }
        return voided;
    }

    public Shipping.AddressCheck validate(String clientId, String storeId, String carrier, Shipping.Address address) {
        String carrierCode = carrier.toUpperCase(Locale.ROOT);
        Accounts.Account account = accounts.resolve(clientId, storeId, carrierCode);
        return requestLog.timed(clientId, storeId, "ADDRESS", carrierCode, address.postalCode(), () -> adapterFor(carrierCode).validate(account, address));
    }

    public Shipping.Tracking track(String clientId, String storeId, String carrier, String trackingNumber) {
        String carrierCode = carrier.toUpperCase(Locale.ROOT);
        Accounts.Account account = accounts.resolve(clientId, storeId, carrierCode);
        return requestLog.timed(clientId, storeId, "TRACK", carrierCode, trackingNumber, () -> adapterFor(carrierCode).track(account, trackingNumber));
    }

    private CarrierAdapter adapterFor(String carrierCode) {
        CarrierAdapter adapter = carrierByCode.get(carrierCode);
        if (adapter == null) {
            throw new IllegalArgumentException("Unknown carrier " + carrierCode + "; the gateway has " + carrierByCode.keySet());
        }
        return adapter;
    }
}
