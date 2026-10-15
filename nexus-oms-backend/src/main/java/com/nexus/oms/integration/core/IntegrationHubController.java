package com.nexus.oms.integration.core;

import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.integration.batch.BatchJobService;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Connections to outside platforms. They are the organisation's own integration stores (persisted, tenant-scoped,
 * secrets sealed), opened on demand. Nothing here reports a result the platform did not give: a test is a real
 * call, an operation the connector does not implement answers 501, and "connected" means the last test passed.
 */
@RestController
@RequestMapping("/integration-hub")
public class IntegrationHubController {

    private final ConnectorRegistry registry;
    private final StoreConnectorService connections;
    private final BatchJobService batchJobService;

    public IntegrationHubController(ConnectorRegistry registry, StoreConnectorService connections,
                                    BatchJobService batchJobService) {
        this.registry = registry;
        this.connections = connections;
        this.batchJobService = batchJobService;
    }

    @GetMapping("/platforms")
    public ResponseEntity<List<ConnectorMetadata>> listAvailablePlatforms() {
        return ResponseEntity.ok(registry.getAvailablePlatforms());
    }

    /**
     * Every outside system Nexus knows of, what it can honestly do with each, and the evidence behind each claim.
     * Static and guarded by tests; it contains no tenant data. {@code liveVerified} is false until a capability has
     * been exercised against the real platform.
     */
    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> catalog() {
        List<IntegrationCatalog.Entry> all = IntegrationCatalog.entries();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupOrder", IntegrationCatalog.GROUP_ORDER);
        out.put("entries", all);
        long native_ = all.stream().filter(e -> e.tier().equals("NATIVE")).count();
        long connectors = all.stream().filter(e -> e.tier().equals("CONNECTOR")).count();
        long generic = all.stream().filter(e -> e.tier().equals("GENERIC")).count();
        out.put("counts", Map.of("native", native_, "connector", connectors, "generic", generic, "total", all.size()));
        out.put("evidenceLegend", Map.of(
                "TESTED", "Automated tests run the logic with the outside system mocked.",
                "CODE_ONLY", "Written, but no automated test covers it.",
                "NOT_IMPLEMENTED", "Nexus does nothing with this."));
        long live = IntegrationCatalog.entries().stream().flatMap(e -> e.capabilities().stream()).filter(IntegrationCatalog.Capability::liveVerified).count();
        out.put("liveVerifiedNote", live == 0
                ? "No capability has yet been run against the real platform with real credentials."
                : live + " capabilities have been run against a real platform (listed below with the date and what was compared). Everything else is not yet run against the live platform.");
        return ResponseEntity.ok(out);
    }

    @GetMapping("/connectors")
    public ResponseEntity<List<Map<String, Object>>> listConnectors() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (NxIntegrationStore s : connections.list()) result.add(summary(s));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/connectors/{id}")
    public ResponseEntity<Map<String, Object>> getConnector(@PathVariable String id) {
        NxIntegrationStore s = connections.find(id);
        Map<String, Object> detail = summary(s);
        detail.put("metadata", connections.open(id).getMetadata());
        return ResponseEntity.ok(detail);
    }

    /** Saves the connection (secrets sealed) and immediately tests it for real. Saved does not mean working: see connectionVerified. */
    @PostMapping("/connectors")
    public ResponseEntity<Map<String, Object>> createConnector(@RequestBody ConnectorConfig config) {
        NxIntegrationStore s = connections.create(config);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.getId().toString());
        out.put("platform", s.getPlatform());
        out.put("saved", true);
        out.putAll(runTest(s));
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/connectors/{id}")
    public ResponseEntity<Void> removeConnector(@PathVariable String id) {
        connections.remove(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/connectors/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connectorId", id);
        out.putAll(runTest(connections.find(id)));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/connectors/{id}/sync/{syncType}")
    public ResponseEntity<?> runSync(@PathVariable String id, @PathVariable String syncType,
                                     @RequestBody(required = false) Map<String, Object> params) {
        Connector c = connections.open(id);
        if (!c.supportsSyncType(syncType)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                    "error", c.getMetadata().getName() + " does not implement " + syncType + " in Nexus",
                    "implemented", c.getSupportedSyncTypes()));
        }
        return ResponseEntity.ok(batchJobService.submitJob(id, syncType, TenantContext.getCurrentTenantId(), params));
    }

    /** Asks the platform to deliver events to Nexus. {@code baseUrl} must be the public HTTPS address of this server. */
    @PostMapping("/connectors/{id}/webhooks")
    public ResponseEntity<Map<String, String>> registerWebhooks(@PathVariable String id, @RequestParam String baseUrl) {
        if (!baseUrl.startsWith("https://")) {
            return ResponseEntity.badRequest().body(Map.of("error", "baseUrl must be this server's public https:// address; platforms do not deliver webhooks to http or localhost"));
        }
        try {
            connections.open(id).registerWebhooks(baseUrl);
            return ResponseEntity.ok(Map.of("status", "Registration requested", "connectorId", id));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<BatchJobService.BatchJob>> listJobs(@RequestParam(required = false) String connectorId) {
        UUID tenant = TenantContext.getCurrentTenantId();
        List<BatchJobService.BatchJob> jobs = connectorId != null ? batchJobService.getJobsByConnector(connectorId) : batchJobService.getActiveJobs();
        return ResponseEntity.ok(jobs.stream().filter(j -> tenant.equals(j.getTenantId())).toList());
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<BatchJobService.BatchJob> getJob(@PathVariable String jobId) {
        BatchJobService.BatchJob job = batchJobService.getJob(jobId);
        if (job == null || !TenantContext.getCurrentTenantId().equals(job.getTenantId())) throw new ResourceNotFoundException("Job", jobId);
        return ResponseEntity.ok(job);
    }

    /** What this organisation has connected and what the last real test of each said. No platform is reported "UP" without a test. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        List<Map<String, Object>> rows = new ArrayList<>();
        int verified = 0, failing = 0, untested = 0;
        for (NxIntegrationStore s : connections.list()) {
            Map<String, Object> t = connections.lastTest(s);
            String state = t.isEmpty() ? "UNTESTED" : Boolean.TRUE.equals(t.get("lastTestOk")) ? "VERIFIED" : "FAILING";
            if (state.equals("VERIFIED")) verified++; else if (state.equals("FAILING")) failing++; else untested++;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", s.getId().toString());
            r.put("name", s.getStoreName());
            r.put("platform", s.getPlatform());
            r.put("state", state);
            r.putAll(t);
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connections", rows.size());
        out.put("verified", verified);
        out.put("failing", failing);
        out.put("untested", untested);
        out.put("availablePlatforms", registry.getRegisteredPlatforms().size());
        out.put("connectors", rows);
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> runTest(NxIntegrationStore s) {
        boolean ok;
        String message;
        try {
            ok = connections.open(s.getId().toString()).testConnection();
            message = ok ? "The platform accepted the credentials" : "The platform did not accept the connection: check the address and credentials";
        } catch (RuntimeException e) {
            ok = false;
            message = e.getMessage() != null ? e.getMessage() : "Connection test failed";
        }
        connections.recordTest(s, ok, message);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connectionVerified", ok);
        out.put("success", ok);
        out.put("message", message);
        return out;
    }

    private Map<String, Object> summary(NxIntegrationStore s) {
        Connector c = connections.open(s.getId().toString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("platform", s.getPlatform());
        m.put("name", s.getStoreName());
        m.put("category", c.getMetadata().getCategory());
        m.put("supportedSyncTypes", c.getSupportedSyncTypes());
        Map<String, Object> t = connections.lastTest(s);
        m.put("verification", t.isEmpty() ? "UNTESTED" : Boolean.TRUE.equals(t.get("lastTestOk")) ? "VERIFIED" : "FAILING");
        m.putAll(t);
        return m;
    }
}
