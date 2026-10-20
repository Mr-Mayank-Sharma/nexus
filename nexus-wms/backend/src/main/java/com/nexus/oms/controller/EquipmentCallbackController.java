package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.EquipmentCallbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound calls from warehouse equipment (conveyors, sorters, shuttles, robot controllers). Unauthenticated
 * by JWT on purpose: a machine has no user. Each call names its tenant in the URL and proves itself with the
 * system's shared secret in {@code X-Equipment-Key}; a wrong or missing key is refused before anything changes.
 */
@RestController
@RequestMapping("/webhooks/equipment/{tenantId}/{systemId}")
public class EquipmentCallbackController {

    private final EquipmentCallbackService service;

    public EquipmentCallbackController(EquipmentCallbackService service) {
        this.service = service;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> heartbeat(
            @PathVariable UUID tenantId, @PathVariable UUID systemId,
            @RequestHeader(value = "X-Equipment-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body) {
        return run(tenantId, () -> {
            var s = service.heartbeat(systemId, key, body == null ? null : str(body.get("status")));
            return Map.<String, Object>of("status", s.getStatus(), "at", String.valueOf(s.getLastHealthCheckAt()));
        });
    }

    @PostMapping("/commands/{commandId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commandStatus(
            @PathVariable UUID tenantId, @PathVariable UUID systemId, @PathVariable UUID commandId,
            @RequestHeader(value = "X-Equipment-Key", required = false) String key,
            @RequestBody Map<String, Object> body) {
        return run(tenantId, () -> {
            Object result = body.get("result");
            String resultJson = result == null ? null
                    : result instanceof String r ? r : toJson(result);
            var c = service.report(systemId, commandId, key, str(body.get("status")), resultJson, str(body.get("error")));
            return Map.<String, Object>of("commandId", c.getId(), "status", c.getStatus());
        });
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> run(UUID tenantId, java.util.function.Supplier<Map<String, Object>> work) {
        TenantContext.setCurrentTenantId(tenantId);
        try {
            return ResponseEntity.ok(ApiResponse.success(work.get()));
        } catch (EquipmentCallbackService.Rejected r) {
            return ResponseEntity.status(r.httpStatus()).body(ApiResponse.error(r.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static String toJson(Object o) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
        catch (Exception e) { return String.valueOf(o); }
    }
}
