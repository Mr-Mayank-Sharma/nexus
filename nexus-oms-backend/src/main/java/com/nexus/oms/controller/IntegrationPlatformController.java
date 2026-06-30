package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Integration Platform", description = "Integration platform management APIs")
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

    @Operation(summary = "List all integration endpoints")
    @GetMapping("/endpoints")
    public ResponseEntity<ApiResponse<Page<IntegrationEndpoint>>> getAllEndpoints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllEndpoints(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get endpoint by ID")
    @GetMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<IntegrationEndpoint>> getEndpoint(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getEndpoint(id)));
    }

    @Operation(summary = "Create a new endpoint")
    @PostMapping("/endpoints")
    public ResponseEntity<ApiResponse<IntegrationEndpoint>> createEndpoint(@Valid @RequestBody IntegrationEndpoint endpoint) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createEndpoint(endpoint), "Endpoint created"));
    }

    @Operation(summary = "Update an endpoint")
    @PutMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<IntegrationEndpoint>> updateEndpoint(
            @PathVariable UUID id, @Valid @RequestBody IntegrationEndpoint endpoint) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateEndpoint(id, endpoint), "Endpoint updated"));
    }

    @Operation(summary = "Delete an endpoint")
    @DeleteMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(@PathVariable UUID id) {
        integrationPlatformService.deleteEndpoint(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Endpoint deleted"));
    }

    @Operation(summary = "Test an endpoint")
    @PostMapping("/endpoints/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testEndpoint(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.testEndpoint(id), "Endpoint test completed"));
    }

    // ──────────────────────────────────────────────
    // Flows
    // ──────────────────────────────────────────────

    @Operation(summary = "List all integration flows")
    @GetMapping("/flows")
    public ResponseEntity<ApiResponse<Page<IntegrationFlow>>> getAllFlows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllFlows(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get flow by ID")
    @GetMapping("/flows/{id}")
    public ResponseEntity<ApiResponse<IntegrationFlow>> getFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getFlow(id)));
    }

    @Operation(summary = "Create a new flow")
    @PostMapping("/flows")
    public ResponseEntity<ApiResponse<IntegrationFlow>> createFlow(@Valid @RequestBody IntegrationFlow flow) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createFlow(flow), "Flow created"));
    }

    @Operation(summary = "Update a flow")
    @PutMapping("/flows/{id}")
    public ResponseEntity<ApiResponse<IntegrationFlow>> updateFlow(
            @PathVariable UUID id, @Valid @RequestBody IntegrationFlow flow) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateFlow(id, flow), "Flow updated"));
    }

    @Operation(summary = "Delete a flow")
    @DeleteMapping("/flows/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFlow(@PathVariable UUID id) {
        integrationPlatformService.deleteFlow(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Flow deleted"));
    }

    @Operation(summary = "Activate a flow")
    @PostMapping("/flows/{id}/activate")
    public ResponseEntity<ApiResponse<IntegrationFlow>> activateFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.activateFlow(id), "Flow activated"));
    }

    @Operation(summary = "Pause a flow")
    @PostMapping("/flows/{id}/pause")
    public ResponseEntity<ApiResponse<IntegrationFlow>> pauseFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.pauseFlow(id), "Flow paused"));
    }

    @Operation(summary = "Get flow steps")
    @GetMapping("/flows/{id}/steps")
    public ResponseEntity<ApiResponse<List<IntegrationFlowStep>>> getSteps(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getSteps(id)));
    }

    @Operation(summary = "Add a step to a flow")
    @PostMapping("/flows/{id}/steps")
    public ResponseEntity<ApiResponse<IntegrationFlowStep>> addStep(
            @PathVariable UUID id, @Valid @RequestBody IntegrationFlowStep step) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.addStep(id, step), "Step added"));
    }

    @Operation(summary = "Update a flow step")
    @PutMapping("/flows/steps/{id}")
    public ResponseEntity<ApiResponse<IntegrationFlowStep>> updateStep(
            @PathVariable UUID id, @Valid @RequestBody IntegrationFlowStep step) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateStep(id, step), "Step updated"));
    }

    @Operation(summary = "Remove a flow step")
    @DeleteMapping("/flows/steps/{id}")
    public ResponseEntity<ApiResponse<Void>> removeStep(@PathVariable UUID id) {
        integrationPlatformService.removeStep(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Step removed"));
    }

    @SuppressWarnings("unchecked")
    @Operation(summary = "Reorder flow steps")
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

    @Operation(summary = "List all transform mappings")
    @GetMapping("/mappings")
    public ResponseEntity<ApiResponse<Page<IntegrationTransformMapping>>> getAllMappings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllMappings(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get mapping by ID")
    @GetMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<IntegrationTransformMapping>> getMapping(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getMapping(id)));
    }

    @Operation(summary = "Create a new mapping")
    @PostMapping("/mappings")
    public ResponseEntity<ApiResponse<IntegrationTransformMapping>> createMapping(
            @Valid @RequestBody IntegrationTransformMapping mapping) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createMapping(mapping), "Mapping created"));
    }

    @Operation(summary = "Update a mapping")
    @PutMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<IntegrationTransformMapping>> updateMapping(
            @PathVariable UUID id, @Valid @RequestBody IntegrationTransformMapping mapping) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateMapping(id, mapping), "Mapping updated"));
    }

    @Operation(summary = "Transform data using a mapping")
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

    @Operation(summary = "List all validation rules")
    @GetMapping("/validation-rules")
    public ResponseEntity<ApiResponse<List<IntegrationValidationRule>>> getRules() {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getRules()));
    }

    @Operation(summary = "Get validation rules by entity type")
    @GetMapping("/validation-rules/entity")
    public ResponseEntity<ApiResponse<List<IntegrationValidationRule>>> getRulesByEntity(
            @RequestParam String entityType) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getRulesByEntity(entityType)));
    }

    @Operation(summary = "Create a validation rule")
    @PostMapping("/validation-rules")
    public ResponseEntity<ApiResponse<IntegrationValidationRule>> createRule(
            @Valid @RequestBody IntegrationValidationRule rule) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createRule(rule), "Rule created"));
    }

    @Operation(summary = "Update a validation rule")
    @PutMapping("/validation-rules/{id}")
    public ResponseEntity<ApiResponse<IntegrationValidationRule>> updateRule(
            @PathVariable UUID id, @Valid @RequestBody IntegrationValidationRule rule) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.updateRule(id, rule), "Rule updated"));
    }

    @Operation(summary = "Toggle a validation rule")
    @PutMapping("/validation-rules/{id}/toggle")
    public ResponseEntity<ApiResponse<IntegrationValidationRule>> toggleRule(
            @PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.toggleRule(id), "Rule toggled"));
    }

    @Operation(summary = "Validate data against rules")
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

    @Operation(summary = "List all import jobs")
    @GetMapping("/imports")
    public ResponseEntity<ApiResponse<Page<IntegrationImportJob>>> getAllImportJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllImportJobs(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get import job by ID")
    @GetMapping("/imports/{id}")
    public ResponseEntity<ApiResponse<IntegrationImportJob>> getImportJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getImportJob(id)));
    }

    @Operation(summary = "Create an import job")
    @PostMapping("/imports")
    public ResponseEntity<ApiResponse<IntegrationImportJob>> createImportJob(
            @Valid @RequestBody IntegrationImportJob job) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createImportJob(job), "Import job created"));
    }

    @Operation(summary = "Process an import job")
    @PostMapping("/imports/{id}/process")
    public ResponseEntity<ApiResponse<Void>> processImportJob(@PathVariable UUID id) {
        importExportEngine.processImportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Import job processed"));
    }

    @Operation(summary = "Retry an import job")
    @PostMapping("/imports/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retryImportJob(@PathVariable UUID id) {
        integrationPlatformService.retryImportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Import job retry initiated"));
    }

    @Operation(summary = "Cancel an import job")
    @PostMapping("/imports/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelImportJob(@PathVariable UUID id) {
        integrationPlatformService.cancelImportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Import job cancelled"));
    }

    // ──────────────────────────────────────────────
    // Export Jobs
    // ──────────────────────────────────────────────

    @Operation(summary = "List all export jobs")
    @GetMapping("/exports")
    public ResponseEntity<ApiResponse<Page<IntegrationExportJob>>> getAllExportJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAllExportJobs(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get export job by ID")
    @GetMapping("/exports/{id}")
    public ResponseEntity<ApiResponse<IntegrationExportJob>> getExportJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getExportJob(id)));
    }

    @Operation(summary = "Create an export job")
    @PostMapping("/exports")
    public ResponseEntity<ApiResponse<IntegrationExportJob>> createExportJob(
            @Valid @RequestBody IntegrationExportJob job) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.createExportJob(job), "Export job created"));
    }

    @Operation(summary = "Process an export job")
    @PostMapping("/exports/{id}/process")
    public ResponseEntity<ApiResponse<Void>> processExportJob(@PathVariable UUID id) {
        importExportEngine.processExportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Export job processed"));
    }

    @Operation(summary = "Retry an export job")
    @PostMapping("/exports/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retryExportJob(@PathVariable UUID id) {
        integrationPlatformService.retryExportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Export job retry initiated"));
    }

    @Operation(summary = "Cancel an export job")
    @PostMapping("/exports/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelExportJob(@PathVariable UUID id) {
        integrationPlatformService.cancelExportJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Export job cancelled"));
    }

    // ──────────────────────────────────────────────
    // Dead Letter Queue
    // ──────────────────────────────────────────────

    @Operation(summary = "List dead letter queue entries")
    @GetMapping("/dlq")
    public ResponseEntity<ApiResponse<Page<IntegrationDLQ>>> getDLQEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getDLQEntries(PageRequest.of(page, size))));
    }

    @Operation(summary = "Replay a dead letter queue entry")
    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<ApiResponse<Void>> replayDLQEntry(@PathVariable UUID id) {
        dlqManager.retryFromDLQ(id);
        return ResponseEntity.ok(ApiResponse.success(null, "DLQ entry replayed"));
    }

    @Operation(summary = "Ignore a dead letter queue entry")
    @PostMapping("/dlq/{id}/ignore")
    public ResponseEntity<ApiResponse<Void>> ignoreDLQEntry(@PathVariable UUID id) {
        integrationPlatformService.ignoreDLQEntry(id);
        return ResponseEntity.ok(ApiResponse.success(null, "DLQ entry ignored"));
    }

    // ──────────────────────────────────────────────
    // CDC Events
    // ──────────────────────────────────────────────

    @Operation(summary = "Get pending CDC events")
    @GetMapping("/cdc/pending")
    public ResponseEntity<ApiResponse<List<IntegrationCDCEvent>>> getPendingEvents() {
        return ResponseEntity.ok(ApiResponse.success(integrationPlatformService.getPendingEvents()));
    }

    @Operation(summary = "Mark a CDC event as processed")
    @PostMapping("/cdc/{id}/process")
    public ResponseEntity<ApiResponse<Void>> markEventProcessed(@PathVariable UUID id) {
        integrationPlatformService.markEventProcessed(id);
        return ResponseEntity.ok(ApiResponse.success(null, "CDC event marked as processed"));
    }

    // ──────────────────────────────────────────────
    // Audit Logs
    // ──────────────────────────────────────────────

    @Operation(summary = "Get audit logs")
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<Page<IntegrationAuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getAuditLogs(PageRequest.of(page, size))));
    }

    @Operation(summary = "Get audit logs by entity type")
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

    @Operation(summary = "Get integration dashboard data")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIntegrationDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                integrationPlatformService.getIntegrationDashboard()));
    }
}
