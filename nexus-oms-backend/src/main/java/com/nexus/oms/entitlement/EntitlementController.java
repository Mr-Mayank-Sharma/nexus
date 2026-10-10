package com.nexus.oms.entitlement;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** What the signed-in user's organisation has licensed; the UI builds its navigation from it. */
@RestController
@RequestMapping("/entitlements")
public class EntitlementController {

    private final EntitlementService entitlements;

    public EntitlementController(EntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> mine() {
        EntitlementService.Entitlements e = entitlements.forTenant(TenantContext.getCurrentTenantId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("edition", e.edition());
        body.put("modules", e.modules().stream().sorted().toList());
        body.put("validUntil", e.validUntil());
        body.put("expired", e.expired());
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
