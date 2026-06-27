package com.nexus.oms.integration.core;

import com.nexus.oms.integration.batch.BatchJobService;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/integration-hub")
public class IntegrationHubController {

    private final ConnectorRegistry registry;
    private final BatchJobService batchJobService;
    private final CredentialVault credentialVault;

    public IntegrationHubController(ConnectorRegistry registry, BatchJobService batchJobService,
                                     CredentialVault credentialVault) {
        this.registry = registry;
        this.batchJobService = batchJobService;
        this.credentialVault = credentialVault;
    }

    @GetMapping("/platforms")
    public ResponseEntity<List<ConnectorMetadata>> listAvailablePlatforms() {
        return ResponseEntity.ok(registry.getAvailablePlatforms());
    }

    @GetMapping("/connectors")
    public ResponseEntity<List<Map<String, Object>>> listConnectors() {
        List<Map<String, Object>> result = registry.getAllConnectors().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("platform", c.getMetadata().getPlatformType());
                    m.put("name", c.getMetadata().getName());
                    m.put("category", c.getMetadata().getCategory());
                    m.put("status", c.getStatus());
                    m.put("health", c.getHealth());
                    m.put("supportedSyncTypes", c.getSupportedSyncTypes());
                    return m;
                }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/connectors/{id}")
    public ResponseEntity<Map<String, Object>> getConnector(@PathVariable String id) {
        try {
            Connector c = registry.getConnector(id);
            Map<String, Object> detail = new LinkedHashMap<>(c.getStatus());
            detail.put("metadata", c.getMetadata());
            detail.put("health", c.getHealth());
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/connectors")
    public ResponseEntity<Map<String, Object>> createConnector(@RequestBody ConnectorConfig config) {
        try {
            config.setTenantId(TenantContext.getCurrentTenantId().toString());
            Connector connector = registry.createAndInitialize(config);
            return ResponseEntity.ok(Map.of(
                    "id", connector.getId(),
                    "platform", connector.getMetadata().getPlatformType(),
                    "status", "INITIALIZED",
                    "testConnection", connector.testConnection()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/connectors/{id}")
    public ResponseEntity<Void> removeConnector(@PathVariable String id) {
        registry.removeConnector(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/connectors/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable String id) {
        try {
            Connector c = registry.getConnector(id);
            boolean success = c.testConnection();
            return ResponseEntity.ok(Map.of(
                    "connectorId", id,
                    "success", success,
                    "message", success ? "Connection successful" : "Connection failed"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("connectorId", id, "success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/connectors/{id}/sync/{syncType}")
    public ResponseEntity<BatchJobService.BatchJob> runSync(
            @PathVariable String id, @PathVariable String syncType,
            @RequestBody(required = false) Map<String, Object> params) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        BatchJobService.BatchJob job = batchJobService.submitJob(id, syncType, tenantId, params);
        return ResponseEntity.ok(job);
    }

    @PostMapping("/connectors/{id}/webhooks")
    public ResponseEntity<Map<String, String>> registerWebhooks(@PathVariable String id,
                                                                  @RequestParam(defaultValue = "http://localhost:8080") String baseUrl) {
        try {
            Connector c = registry.getConnector(id);
            c.registerWebhooks(baseUrl);
            return ResponseEntity.ok(Map.of("status", "Webhooks registered", "connectorId", id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<BatchJobService.BatchJob>> listJobs(
            @RequestParam(required = false) String connectorId) {
        if (connectorId != null) {
            return ResponseEntity.ok(batchJobService.getJobsByConnector(connectorId));
        }
        return ResponseEntity.ok(batchJobService.getActiveJobs());
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<BatchJobService.BatchJob> getJob(@PathVariable String jobId) {
        BatchJobService.BatchJob job = batchJobService.getJob(jobId);
        return job != null ? ResponseEntity.ok(job) : ResponseEntity.notFound().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("totalConnectors", registry.getConnectorCount());
        health.put("registeredPlatforms", registry.getRegisteredPlatforms().size());
        health.put("activeJobs", batchJobService.getActiveJobs().size());

        List<Map<String, Object>> connectorHealth = registry.getAllConnectors().stream()
                .map(c -> Map.of(
                        "id", c.getId(),
                        "name", c.getMetadata().getName(),
                        "health", c.getHealth().getStatus().name(),
                        "lastSuccess", c.getStatus().get("lastSuccessAt")
                )).toList();
        health.put("connectors", connectorHealth);
        return ResponseEntity.ok(health);
    }
}
