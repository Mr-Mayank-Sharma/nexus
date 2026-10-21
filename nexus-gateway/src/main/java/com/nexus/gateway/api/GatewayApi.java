package com.nexus.gateway.api;

import com.nexus.gateway.carrier.Shipping;
import com.nexus.gateway.carrier.ShippingService;
import com.nexus.gateway.email.EmailService;
import com.nexus.gateway.log.RequestLog;
import com.nexus.gateway.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** What client installations call (header X-Nexus-Gateway-Key). One API for every carrier and the email provider. */
@RestController
@RequestMapping("/v1")
public class GatewayApi {

    private final ShippingService shipping;
    private final EmailService email;
    private final RequestLog log;

    public GatewayApi(ShippingService shipping, EmailService email, RequestLog log) {
        this.shipping = shipping;
        this.email = email;
        this.log = log;
    }

    public record RatesRequest(String storeId, String carrier, Shipping.Address from, Shipping.Address to, List<Shipping.Parcel> parcels) {
    }

    public record LabelBody(String storeId, String carrier, String service, Shipping.Address from, Shipping.Address to, List<Shipping.Parcel> parcels,
                            String reference, String imageFormat, boolean isReturn) {
    }

    public record VoidBody(String storeId, String carrier, String trackingNumber) {
    }

    public record AddressBody(String storeId, String carrier, Shipping.Address address) {
    }

    public record EmailBody(String storeId, String templateId, String to, Map<String, Object> data) {
    }

    @GetMapping("/carriers")
    public List<String> carriers() {
        return shipping.carriers();
    }

    @PostMapping("/rates")
    public List<Shipping.Quote> rates(HttpServletRequest req, @RequestBody RatesRequest r) {
        return shipping.rates(client(req), r.storeId(), r.carrier(), new Shipping.RateRequest(r.from(), r.to(), r.parcels()));
    }

    @PostMapping("/labels")
    public ShippingService.LabelResult label(HttpServletRequest req, @RequestBody LabelBody b) {
        if (b.carrier() == null) throw new IllegalArgumentException("Name the carrier");
        return shipping.label(client(req), b.storeId(), b.carrier(), new Shipping.LabelRequest(b.service(), b.from(), b.to(), b.parcels(), b.reference(),
                b.imageFormat(), b.isReturn()));
    }

    @GetMapping("/labels/{labelId}")
    public ResponseEntity<ShippingService.Document> document(HttpServletRequest req, @PathVariable String labelId) {
        return shipping.document(client(req), labelId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/labels/void")
    public Map<String, Object> voidLabel(HttpServletRequest req, @RequestBody VoidBody b) {
        return Map.of("voided", shipping.voidLabel(client(req), b.storeId(), b.carrier(), b.trackingNumber()));
    }

    @PostMapping("/addresses/validate")
    public Shipping.AddressCheck validate(HttpServletRequest req, @RequestBody AddressBody b) {
        return shipping.validate(client(req), b.storeId(), b.carrier(), b.address());
    }

    @GetMapping("/tracking/{carrier}/{trackingNumber}")
    public Shipping.Tracking track(HttpServletRequest req, @PathVariable String carrier, @PathVariable String trackingNumber,
                                   @RequestParam(required = false) String storeId) {
        return shipping.track(client(req), storeId, carrier, trackingNumber);
    }

    @PostMapping("/email/send")
    public Map<String, String> send(HttpServletRequest req, @RequestBody EmailBody b) {
        return Map.of("messageId", email.send(client(req), b.storeId(), b.templateId(), b.to(), b.data()));
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> requests(HttpServletRequest req, @RequestParam(defaultValue = "50") int limit) {
        return log.recent(client(req), Math.min(limit, 500));
    }

    static String client(HttpServletRequest req) {
        return (String) req.getAttribute(AuthFilter.CLIENT);
    }
}
