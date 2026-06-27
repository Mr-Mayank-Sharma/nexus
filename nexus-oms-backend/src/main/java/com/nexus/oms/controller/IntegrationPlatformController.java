package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/integration-platform")
public class IntegrationPlatformController {

    private final IntegrationPlatformService integrationPlatformService;
    private final ImportExportEngine importExportEngine;
    private final TransformationEngine transformationEngine;
    private final ValidationEngine validationEngine;
    private final DLQManager dlqManager;
    private final CDCProcessor cdcProcessor;

    public IntegrationPlatformController(IntegrationPlatformService integrationPlatformService,
                                         ImportExportEngine importExportEngine,
                                         TransformationEngine transformationEngine,
                                         ValidationEngine validationEngine,
                                         DLQManager dlqManager,
                                         CDCProcessor cdcProcessor) {
        this.integrationPlatformService = integrationPlatformService;
        this.importExportEngine = importExportEngine;
        this.transformationEngine = transformationEngine;
        this.validationEngine = validationEngine;
        this.dlqManager = dlqManager;
        this.cdcProcessor = cdcProcessor;
    }

    // ──────────────────────────────────────────────
    // Endpoints
    // ──────────────────────────────────────────────

    @GetMapping("/endpoints")
    public ResponseEntity<ApiResponse<Page<IntegrationEndpoint>>> getAllEndpoints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllEndpoints(PageRequest.of(page, size))));
    }

    @GetMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<IntegrationEndpoint>> getEndpoint(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getEndpoint(id)));
    }

    @PostMapping("/endpoints")
    public ResponseEntity<ApiResponse<IntegrationEndpoint>> createEndpoint(@RequestBody IntegrationEndpoint endpoint) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createEndpoint(endpoint), "Endpoint created"));
    }

    @PutMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<IntegrationEndpoint>> updateEndpoint(
            @PathVariable UUID id, @RequestBody IntegrationEndpoint endpoint) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateEndpoint(id, endpoint), "Endpoint updated"));
    }

    @DeleteMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(@PathVariable UUID id) {
        integrationPlatformService.deleteEndpoint(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Endpoint deleted"));
    }

    @PostMapping("/endpoints/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testEndpoint(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.testEndpoint(id), "Endpoint test completed"));
    }

    // ──────────────────────────────────────────────
    // Flows
    // ──────────────────────────────────────────────

    @GetMapping("/flows")
    public ResponseEntity<ApiResponse<Page<IntegrationFlow>>> getAllFlows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllFlows(PageRequest.of(page, size))));
    }

    @GetMapping("/flows/{id}")
    public ResponseEntity<ApiResponse<IntegrationFlow>> getFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getFlow(id)));
    }

    @PostMapping("/flows")
    public ResponseEntity<ApiResponse<IntegrationFlow>> createFlow(@RequestBody IntegrationFlow flow) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createFlow(flow), "Flow created"));
    }

    @PutMapping("/flows/{id}")
    public ResponseEntity<ApiResponse<IntegrationFlow>> updateFlow(
            @PathVariable UUID id, @RequestBody IntegrationFlow flow) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateFlow(id, flow), "Flow updated"));
    }

    @DeleteMapping("/flows/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFlow(@PathVariable UUID id) {
        integrationPlatformService.deleteFlow(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Flow deleted"));
    }

    @PostMapping("/flows/{id}/activate")
    public ResponseEntity<ApiResponse<IntegrationFlow>> activateFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.activateFlow(id), "Flow activated"));
    }

    @PostMapping("/flows/{id}/pause")
    public ResponseEntity<ApiResponse<IntegrationFlow>> pauseFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.pauseFlow(id), "Flow paused"));
    }

    @GetMapping("/flows/{id}/steps")
    public ResponseEntity<ApiResponse<List<IntegrationFlowStep>>> getSteps(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getSteps(id)));
    }

    @PostMapping("/flows/{id}/steps")
    public ResponseEntity<ApiResponse<IntegrationFlowStep>> addStep(
            @PathVariable UUID id, @RequestBody IntegrationFlowStep step) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.addStep(id, step), "Step added"));
    }

    @PutMapping("/flows/steps/{id}")
    public ResponseEntity<ApiResponse<IntegrationFlowStep>> updateStep(
            @PathVariable UUID id, @RequestBody IntegrationFlowStep step) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateStep(id, step), "Step updated"));
    }

    @DeleteMapping("/flows/steps/{id}")
    public ResponseEntity<ApiResponse<Void>> removeStep(@PathVariable UUID id) {
        integrationPlatformService.removeStep(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Step removed"));
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/flows/{id}/steps/reorder")
    public ResponseEntity<ApiResponse<List<IntegrationFlowStep>>> reorderSteps(
            @PathVariable UUID id, @RequestBody List<Map<String, Object>> steps) {
        List<UUID> stepIds = steps.stream()
                .map(s -> UUID.fromString((String) s.get("stepId")))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.reorderSteps(id, stepIds), "Steps reordered"));
    }

    // ──────────────────────────────────────────────
    // Transform Mappings
    // ──────────────────────────────────────────────

    @GetMapping("/mappings")
    public ResponseEntity<ApiResponse<Page<IntegrationTransformMapping>>> getAllMappings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllMappings(PageRequest.of(page, size))));
    }

    @GetMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<IntegrationTransformMapping>> getMapping(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getMapping(id)));
    }

    @PostMapping("/mappings")
    public ResponseEntity<ApiResponse<IntegrationTransformMapping>> createMapping(
            @RequestBody IntegrationTransformMapping mapping) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createMapping(mapping), "Mapping created"));
    }

    @PutMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<IntegrationTransformMapping>> updateMapping(
            @PathVariable UUID id, @RequestBody IntegrationTransformMapping mapping) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateMapping(id, mapping), "Mapping updated"));
    }

    @PostMapping("/mappings/{id}/transform")
    public ResponseEntity<ApiResponse<String>> transform(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        String result = transformationEngine.transform(
                request.get("payload"),
                request.get("sourceFormat"),
                request.get("targetFormat"),
                id);
        return ResponseEntity.ok(ApiResponse.success(result, "Transformation completed"));
    }

    // ──────────────────────────────────────────────
    // Validation Rules
    // ──────────────────────────────────────────────

    @GetMapping("/validation-rules")
    public ResponseEntity<ApiResponse<List<IntegrationValidationRule>>> getRules() {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getRules()));
    }

    @GetMapping("/validation-rules/entity")
    public ResponseEntity<ApiResponse<List<IntegrationValidationRule>>> getRulesByEntity(
            @RequestParam String entityType) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getRulesByEntity(entityType)));
    }

    @PostMapping("/validation-rules")
    public ResponseEntity<ApiResponse<IntegrationValidationRule>> createRule(
            @RequestBody IntegrationValidationRule rule) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createRule(rule), "Rule created"));
    }

    @PutMapping("/validation-rules/{id}")
    public ResponseEntity<ApiResponse<IntegrationValidationRule>> updateRule(
            @PathVariable UUID id, @RequestBody IntegrationValidationRule rule) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateRule(id, rule), "Rule updated"));
    }

    @PutMapping("/validation-rules/{id}/toggle")
    public ResponseEntity<ApiResponse<IntegrationValidationRule>> toggleRule(
            @PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.toggleRule(id), "Rule toggled"));
    }

    @PostMapping("/validation-rules/validate")
    public ResponseEntity<ApiResponse<List<String>>> validate(
            @RequestBody Map<String, String> request) {
        List<String> errors = validationEngine.validate(
                request.get("payload"),
                request.get("entityType"),
                TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(errors, "Validation completed"));
    }

    // ──────────────────────────────────────────────
    // Import Jobs
    // ──────────────────────────────────────────────

    @GetMapping("/imports")
    public ResponseEntity<ApiResponse<Page<IntegrationImportJob>>> getAllImportJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllImportJobs(PageRequest.of(page, size))));
    }

    @GetMapping("/imports/{id}")
    public ResponseEntity<ApiResponse<IntegrationImportJob>> getImportJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getImportJob(id)));
    }

    @PostMapping("/imports")
    public ResponseEntity<ApiResponse<IntegrationImportJob>> createImportJob(
            @RequestBody IntegrationImportJob job) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createImportJob(job), "Import job created"));
    }

    @PostMapping("/imports/{id}/process")
    public ResponseEntity<ApiResponse<Void>> processImportJob(@PathVariable UUID id) {
        importExportEngine.processImportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Import job processed"));
    }

    @PostMapping("/imports/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retryImportJob(@PathVariable UUID id) {
        integrationPlatformService.retryImportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Import job retry initiated"));
    }

    @PostMapping("/imports/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelImportJob(@PathVariable UUID id) {
        integrationPlatformService.cancelImportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Import job cancelled"));
    }

    // ──────────────────────────────────────────────
    // Export Jobs
    // ──────────────────────────────────────────────

    @GetMapping("/exports")
    public ResponseEntity<ApiResponse<Page<IntegrationExportJob>>> getAllExportJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllExportJobs(PageRequest.of(page, size))));
    }

    @GetMapping("/exports/{id}")
    public ResponseEntity<ApiResponse<IntegrationExportJob>> getExportJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getExportJob(id)));
    }

    @PostMapping("/exports")
    public ResponseEntity<ApiResponse<IntegrationExportJob>> createExportJob(
            @RequestBody IntegrationExportJob job) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createExportJob(job), "Export job created"));
    }

    @PostMapping("/exports/{id}/process")
    public ResponseEntity<ApiResponse<Void>> processExportJob(@PathVariable UUID id) {
        importExportEngine.processExportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Export job processed"));
    }

    @PostMapping("/exports/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retryExportJob(@PathVariable UUID id) {
        integrationPlatformService.retryExportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Export job retry initiated"));
    }

    @PostMapping("/exports/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelExportJob(@PathVariable UUID id) {
        integrationPlatformService.cancelExportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Export job cancelled"));
    }

    // ──────────────────────────────────────────────
    // Dead Letter Queue
    // ──────────────────────────────────────────────

    @GetMapping("/dlq")
    public ResponseEntity<ApiResponse<Page<IntegrationDLQ>>> getDLQEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getDLQEntries(PageRequest.of(page, size))));
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<ApiResponse<Void>> replayDLQEntry(@PathVariable UUID id) {
        dlqManager.retryFromDLQ(id);
        return ResponseEntity.ok(ApiResponse.success(null, "DLQ entry replayed"));
    }

    @PostMapping("/dlq/{id}/ignore")
    public ResponseEntity<ApiResponse<Void>> ignoreDLQEntry(@PathVariable UUID id) {
        integrationPlatformService.ignoreDLQEntry(id);
        return ResponseEntity.ok(ApiResponse.success(null, "DLQ entry ignored"));
    }

    // ──────────────────────────────────────────────
    // CDC Events
    // ──────────────────────────────────────────────

    @GetMapping("/cdc/pending")
    public ResponseEntity<ApiResponse<List<IntegrationCDCEvent>>> getPendingEvents() {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getPendingEvents()));
    }

    @PostMapping("/cdc/{id}/process")
    public ResponseEntity<ApiResponse<Void>> markEventProcessed(@PathVariable UUID id) {
        integrationPlatformService.markEventProcessed(id);
        return ResponseEntity.ok(ApiResponse.success(null, "CDC event marked as processed"));
    }

    // ──────────────────────────────────────────────
    // Audit Logs
    // ──────────────────────────────────────────────

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<Page<IntegrationAuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAuditLogs(PageRequest.of(page, size))));
    }

    @GetMapping("/audit/entity")
    public ResponseEntity<ApiResponse<Page<IntegrationAuditLog>>> getAuditLogsByEntity(
            @RequestParam String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAuditLogsByEntity(entityType, PageRequest.of(page, size))));
    }

    // ──────────────────────────────────────────────
    // Dashboard
    // ──────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIntegrationDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getIntegrationDashboard()));
    }
}
