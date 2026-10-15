package com.nexus.oms.pos;

import com.nexus.oms.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Operator-facing: register a store's POS source, see how its counts compare with Nexus, reconcile. */
@RestController
@RequestMapping("/pos")
public class PosController {

    private final PosInventoryService service;

    public PosController(PosInventoryService service) {
        this.service = service;
    }

    @GetMapping("/sources")
    public ResponseEntity<ApiResponse<List<NxPosSource>>> sources() {
        return ResponseEntity.ok(ApiResponse.success(service.listSources()));
    }

    /** The secret in the response is shown once; it is stored only as a hash. */
    @PostMapping("/sources")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        UUID nodeId = UUID.fromString(String.valueOf(body.get("nodeId")));
        var created = service.createSource(nodeId, (String) body.get("name"), (String) body.get("vendor"),
                body.get("staleAfterMinutes") == null ? null : ((Number) body.get("staleAfterMinutes")).intValue());
        return ResponseEntity.ok(ApiResponse.success(withSecret(created)));
    }

    @PostMapping("/sources/{id}/rotate-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rotate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(withSecret(service.rotateKey(id))));
    }

    @GetMapping("/inventory/aggregate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aggregate(@RequestParam UUID nodeId) {
        List<PosInventoryService.Row> rows = service.aggregate(nodeId);
        Map<String, Long> counts = new TreeMap<>();
        rows.forEach(r -> counts.merge(r.status(), 1L, Long::sum));
        return ResponseEntity.ok(ApiResponse.success(Map.of("nodeId", nodeId, "summary", counts, "rows", rows)));
    }

    /** Dry run unless {@code apply} is true. */
    @PostMapping("/inventory/reconcile")
    public ResponseEntity<ApiResponse<PosInventoryService.ReconcileResult>> reconcile(@RequestBody Map<String, Object> body) {
        UUID nodeId = UUID.fromString(String.valueOf(body.get("nodeId")));
        @SuppressWarnings("unchecked") List<String> skus = (List<String>) body.get("skus");
        boolean apply = Boolean.TRUE.equals(body.get("apply"));
        return ResponseEntity.ok(ApiResponse.success(service.reconcile(nodeId, skus, apply)));
    }

    private static Map<String, Object> withSecret(PosInventoryService.Created c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", c.source());
        m.put("secret", c.secret());
        m.put("note", "Store this secret now; it cannot be shown again.");
        return m;
    }
}
