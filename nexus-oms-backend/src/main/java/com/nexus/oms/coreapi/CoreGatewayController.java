package com.nexus.oms.coreapi;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.gateway.GatewayService;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Nexus Gateway from the OMS (doc 26, W11): point a store at a gateway, make labels, send a test email. Admin only. */
@RestController
@RequestMapping("/core/gateway")
@Tag(name = "Core gateway", description = "Carrier labels and customer emails through the Nexus Gateway")
public class CoreGatewayController {

    private final GatewayService gateway;
    private final com.nexus.core.service.AgentCommerceService agents;

    public CoreGatewayController(GatewayService gateway, com.nexus.core.service.AgentCommerceService agents) {
        this.gateway = gateway;
        this.agents = agents;
    }

    @Operation(summary = "Issue the key AI shopping agents use for a store (shown once; the old one stops working)")
    @PostMapping("/stores/{productStoreId}/agent-key")
    public ResponseEntity<ApiResponse<String>> agentKey(@PathVariable String productStoreId) {
        return ResponseEntity.ok(ApiResponse.success(agents.issueKey(productStoreId), "Keep this key in the agent platform; it is not shown again"));
    }

    public record StoreGateway(String url, String apiKey, String defaultCarrier) {
    }

    public record LabelBody(String carrier, String service) {
    }

    public record TestEmail(String to, String templateId) {
    }

    @Operation(summary = "Point a store at a gateway (the key is stored encrypted and never shown again)")
    @PutMapping("/stores/{productStoreId}")
    public ResponseEntity<ApiResponse<Void>> configure(@PathVariable String productStoreId, @RequestBody StoreGateway g) {
        gateway.configure(productStoreId, g.url(), g.apiKey(), g.defaultCarrier());
        return ResponseEntity.ok(ApiResponse.success(null, "Gateway set for " + productStoreId));
    }

    @Operation(summary = "Make carrier labels for a shipment through its store's gateway")
    @PostMapping("/shipments/{shipmentId}/label")
    public ResponseEntity<ApiResponse<GatewayService.LabelOutcome>> label(@PathVariable String shipmentId, @RequestBody(required = false) LabelBody b) {
        return ResponseEntity.ok(ApiResponse.success(gateway.label(shipmentId, b == null ? null : b.carrier(), b == null ? null : b.service(), user()), "Labels made"));
    }

    @Operation(summary = "Send a test email through a store's gateway")
    @PostMapping("/stores/{productStoreId}/test-email")
    public ResponseEntity<ApiResponse<String>> testEmail(@PathVariable String productStoreId, @RequestBody TestEmail t) {
        return ResponseEntity.ok(ApiResponse.success(gateway.testEmail(productStoreId, t.templateId(), t.to()), "Test email sent"));
    }

    private static String user() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
