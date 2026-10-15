package com.nexus.oms.pos;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Called by a store's POS or its middleware; no user, so it is outside JWT like the other /webhooks.
 * Body: {"reportedAt": "2026-10-15T09:30:00Z" (optional), "items": [{"sku": "...", "quantity": 12}, ...]}
 * Header: X-Pos-Key: the secret shown when the source was created.
 */
@RestController
@RequestMapping("/webhooks/pos/{tenantId}/{sourceId}")
public class PosIngestController {

    private final PosInventoryService service;

    public PosIngestController(PosInventoryService service) {
        this.service = service;
    }

    @PostMapping("/inventory")
    public ResponseEntity<ApiResponse<Map<String, Object>>> push(
            @PathVariable UUID tenantId, @PathVariable UUID sourceId,
            @RequestHeader(value = "X-Pos-Key", required = false) String key,
            @RequestBody Map<String, Object> body) {
        TenantContext.setCurrentTenantId(tenantId);
        try {
            LocalDateTime reportedAt = null;
            if (body.get("reportedAt") != null) {
                try {
                    reportedAt = OffsetDateTime.parse(String.valueOf(body.get("reportedAt"))).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                } catch (RuntimeException e) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("reportedAt must be an ISO-8601 timestamp with an offset"));
                }
            }
            List<PosInventoryService.Item> items = new ArrayList<>();
            if (body.get("items") instanceof List<?> raw) {
                for (Object o : raw) {
                    if (o instanceof Map<?, ?> m) {
                        Object q = m.get("quantity");
                        items.add(new PosInventoryService.Item(m.get("sku") == null ? null : m.get("sku").toString(),
                                q instanceof Number n && n.doubleValue() == Math.floor(n.doubleValue()) ? Integer.valueOf(n.intValue()) : null));
                    } else {
                        items.add(null);
                    }
                }
            }
            return ResponseEntity.ok(ApiResponse.success(service.ingest(sourceId, key, reportedAt, items)));
        } catch (PosInventoryService.Rejected r) {
            return ResponseEntity.status(r.httpStatus()).body(ApiResponse.error(r.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }
}
