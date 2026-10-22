package com.nexus.gateway.api;

import com.nexus.gateway.carrier.LabelDocuments;
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

    private final ShippingService shippingService;
    private final EmailService emailService;
    private final RequestLog requestLog;

    public GatewayApi(ShippingService shippingService, EmailService emailService, RequestLog requestLog) {
        this.shippingService = shippingService;
        this.emailService = emailService;
        this.requestLog = requestLog;
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
        return shippingService.carriers();
    }

    @PostMapping("/rates")
    public List<Shipping.Quote> rates(HttpServletRequest request, @RequestBody RatesRequest ratesRequest) {
        Shipping.RateRequest rateRequest = new Shipping.RateRequest(ratesRequest.from(), ratesRequest.to(), ratesRequest.parcels());
        return shippingService.rates(clientOf(request), ratesRequest.storeId(), ratesRequest.carrier(), rateRequest);
    }

    @PostMapping("/labels")
    public ShippingService.LabelResult label(HttpServletRequest request, @RequestBody LabelBody body) {
        if (body.carrier() == null) {
            throw new IllegalArgumentException("Name the carrier");
        }
        Shipping.LabelRequest labelRequest = new Shipping.LabelRequest(body.service(), body.from(), body.to(), body.parcels(), body.reference(),
                body.imageFormat(), body.isReturn());
        return shippingService.label(clientOf(request), body.storeId(), body.carrier(), labelRequest);
    }

    @GetMapping("/labels/{labelId}")
    public ResponseEntity<LabelDocuments.Document> document(HttpServletRequest request, @PathVariable String labelId) {
        return shippingService.document(clientOf(request), labelId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/labels/void")
    public Map<String, Object> voidLabel(HttpServletRequest request, @RequestBody VoidBody body) {
        return Map.of("voided", shippingService.voidLabel(clientOf(request), body.storeId(), body.carrier(), body.trackingNumber()));
    }

    @PostMapping("/addresses/validate")
    public Shipping.AddressCheck validate(HttpServletRequest request, @RequestBody AddressBody body) {
        return shippingService.validate(clientOf(request), body.storeId(), body.carrier(), body.address());
    }

    @GetMapping("/tracking/{carrier}/{trackingNumber}")
    public Shipping.Tracking track(HttpServletRequest request, @PathVariable String carrier, @PathVariable String trackingNumber,
                                   @RequestParam(required = false) String storeId) {
        return shippingService.track(clientOf(request), storeId, carrier, trackingNumber);
    }

    @PostMapping("/email/send")
    public Map<String, String> send(HttpServletRequest request, @RequestBody EmailBody body) {
        return Map.of("messageId", emailService.send(clientOf(request), body.storeId(), body.templateId(), body.to(), body.data()));
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> requests(HttpServletRequest request, @RequestParam(defaultValue = "50") int limit) {
        return requestLog.recent(clientOf(request), Math.min(limit, 500));
    }

    /** The client the auth filter found for the request's key. */
    static String clientOf(HttpServletRequest request) {
        return (String) request.getAttribute(AuthFilter.CLIENT);
    }
}
