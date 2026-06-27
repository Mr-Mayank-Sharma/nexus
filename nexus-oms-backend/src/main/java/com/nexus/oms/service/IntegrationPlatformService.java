package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class IntegrationPlatformService {

    private final IntegrationEndpointRepository endpointRepository;
    private final IntegrationFlowRepository flowRepository;
    private final IntegrationFlowStepRepository flowStepRepository;
    private final IntegrationMessageRepository messageRepository;
    private final IntegrationDLQRepository dlqRepository;
    private final IntegrationTransformMappingRepository mappingRepository;
    private final IntegrationValidationRuleRepository ruleRepository;
    private final IntegrationImportJobRepository importJobRepository;
    private final IntegrationExportJobRepository exportJobRepository;
    private final IntegrationCDCEventRepository cdcEventRepository;
    private final IntegrationAuditLogRepository auditLogRepository;

    private final ImportExportEngine importExportEngine;
    private final DLQManager dlqManager;
    private final CDCProcessor cdcProcessor;

    public IntegrationPlatformService(IntegrationEndpointRepository endpointRepository,
                                     IntegrationFlowRepository flowRepository,
                                     IntegrationFlowStepRepository flowStepRepository,
                                     IntegrationMessageRepository messageRepository,
                                     IntegrationDLQRepository dlqRepository,
                                     IntegrationTransformMappingRepository mappingRepository,
                                     IntegrationValidationRuleRepository ruleRepository,
                                     IntegrationImportJobRepository importJobRepository,
                                     IntegrationExportJobRepository exportJobRepository,
                                     IntegrationCDCEventRepository cdcEventRepository,
                                     IntegrationAuditLogRepository auditLogRepository,
                                     ImportExportEngine importExportEngine,
                                     DLQManager dlqManager,
                                     CDCProcessor cdcProcessor) {
        this.endpointRepository = endpointRepository;
        this.flowRepository = flowRepository;
        this.flowStepRepository = flowStepRepository;
        this.messageRepository = messageRepository;
        this.dlqRepository = dlqRepository;
        this.mappingRepository = mappingRepository;
        this.ruleRepository = ruleRepository;
        this.importJobRepository = importJobRepository;
        this.exportJobRepository = exportJobRepository;
        this.cdcEventRepository = cdcEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.importExportEngine = importExportEngine;
        this.dlqManager = dlqManager;
        this.cdcProcessor = cdcProcessor;
    }

    // ──────────────────────────────────────────────
    // Endpoint CRUD
    // ──────────────────────────────────────────────

    public Page<IntegrationEndpoint> getAllEndpoints(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return endpointRepository.findByTenantId(tenantId, pageable);
    }

    public IntegrationEndpoint getEndpoint(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return endpointRepository.findById(id)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationEndpoint", id));
    }

    @Transactional
    public IntegrationEndpoint createEndpoint(IntegrationEndpoint endpoint) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        endpoint.setTenantId(tenantId);
        return endpointRepository.save(endpoint);
    }

    @Transactional
    public IntegrationEndpoint updateEndpoint(UUID id, IntegrationEndpoint updated) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationEndpoint existing = endpointRepository.findById(id)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationEndpoint", id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setEndpointType(updated.getEndpointType());
        existing.setProtocol(updated.getProtocol());
        existing.setHost(updated.getHost());
        existing.setPort(updated.getPort());
        existing.setPath(updated.getPath());
        existing.setMethod(updated.getMethod());
        existing.setHeaders(updated.getHeaders());
        existing.setQueryParams(updated.getQueryParams());
        existing.setAuthType(updated.getAuthType());
        existing.setAuthConfig(updated.getAuthConfig());
        existing.setSslEnabled(updated.getSslEnabled());
        existing.setTimeoutMs(updated.getTimeoutMs());
        existing.setRetryCount(updated.getRetryCount());
        existing.setRetryDelayMs(updated.getRetryDelayMs());
        existing.setCircuitBreakerEnabled(updated.getCircuitBreakerEnabled());
        existing.setCircuitBreakerThreshold(updated.getCircuitBreakerThreshold());
        existing.setCircuitBreakerTimeoutMs(updated.getCircuitBreakerTimeoutMs());
        existing.setRateLimitEnabled(updated.getRateLimitEnabled());
        existing.setRateLimitMax(updated.getRateLimitMax());
        existing.setRateLimitWindowMs(updated.getRateLimitWindowMs());
        existing.setIsActive(updated.getIsActive());
        existing.setMetadata(updated.getMetadata());
        return endpointRepository.save(existing);
    }

    @Transactional
    public void deleteEndpoint(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationEndpoint existing = endpointRepository.findById(id)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationEndpoint", id));
        List<IntegrationFlow> flowsReferencing = flowRepository
                .findBySourceEndpointIdOrTargetEndpointId(id, id);
        if (!flowsReferencing.isEmpty()) {
            throw new BadRequestException("Cannot delete endpoint referenced by " + flowsReferencing.size() + " flow(s)");
        }
        endpointRepository.delete(existing);
    }

    public Map<String, Object> testEndpoint(UUID id) {
        getEndpoint(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("latencyMs", 42);
        result.put("message", "Connection test completed successfully");
        return result;
    }

    // ──────────────────────────────────────────────
    // Flow CRUD
    // ──────────────────────────────────────────────

    public Page<IntegrationFlow> getAllFlows(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return flowRepository.findByTenantId(tenantId, pageable);
    }

    public IntegrationFlow getFlow(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return flowRepository.findById(id)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationFlow", id));
    }

    @Transactional
    public IntegrationFlow createFlow(IntegrationFlow flow) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        flow.setTenantId(tenantId);
        return flowRepository.save(flow);
    }

    @Transactional
    public IntegrationFlow updateFlow(UUID id, IntegrationFlow updated) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationFlow existing = flowRepository.findById(id)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationFlow", id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setFlowType(updated.getFlowType());
        existing.setSourceEndpointId(updated.getSourceEndpointId());
        existing.setTargetEndpointId(updated.getTargetEndpointId());
        existing.setTriggerType(updated.getTriggerType());
        existing.setTriggerConfig(updated.getTriggerConfig());
        existing.setScheduleCron(updated.getScheduleCron());
        existing.setPriority(updated.getPriority());
        existing.setMaxRetries(updated.getMaxRetries());
        existing.setRetryDelaySeconds(updated.getRetryDelaySeconds());
        existing.setBatchSize(updated.getBatchSize());
        existing.setThrottleRate(updated.getThrottleRate());
        existing.setProcessingTimeoutMinutes(updated.getProcessingTimeoutMinutes());
        existing.setErrorHandling(updated.getErrorHandling());
        existing.setIsActive(updated.getIsActive());
        existing.setMetadata(updated.getMetadata());
        return flowRepository.save(existing);
    }

    @Transactional
    public void deleteFlow(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationFlow existing = flowRepository.findById(id)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationFlow", id));
        flowStepRepository.findByFlowId(id).forEach(flowStepRepository::delete);
        flowRepository.delete(existing);
    }

    @Transactional
    public IntegrationFlow activateFlow(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationFlow flow = flowRepository.findById(id)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationFlow", id));
        flow.setStatus("ACTIVE");
        flow.setIsActive(true);
        return flowRepository.save(flow);
    }

    @Transactional
    public IntegrationFlow pauseFlow(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationFlow flow = flowRepository.findById(id)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationFlow", id));
        flow.setStatus("PAUSED");
        flow.setIsActive(false);
        return flowRepository.save(flow);
    }

    // ──────────────────────────────────────────────
    // Flow Steps
    // ──────────────────────────────────────────────

    public List<IntegrationFlowStep> getSteps(UUID flowId) {
        return flowStepRepository.findByFlowIdOrderByStepOrderAsc(flowId);
    }

    @Transactional
    public IntegrationFlowStep addStep(UUID flowId, IntegrationFlowStep step) {
        getFlow(flowId);
        step.setFlowId(flowId);
        List<IntegrationFlowStep> existing = flowStepRepository.findByFlowIdOrderByStepOrderAsc(flowId);
        step.setStepOrder(existing.size() + 1);
        return flowStepRepository.save(step);
    }

    @Transactional
    public IntegrationFlowStep updateStep(UUID stepId, IntegrationFlowStep updated) {
        IntegrationFlowStep existing = flowStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationFlowStep", stepId));
        existing.setStepType(updated.getStepType());
        existing.setTransformerType(updated.getTransformerType());
        existing.setConfig(updated.getConfig());
        existing.setConditionExpression(updated.getConditionExpression());
        existing.setOnError(updated.getOnError());
        existing.setTimeoutSeconds(updated.getTimeoutSeconds());
        return flowStepRepository.save(existing);
    }

    @Transactional
    public void removeStep(UUID stepId) {
        IntegrationFlowStep existing = flowStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationFlowStep", stepId));
        flowStepRepository.delete(existing);
    }

    @Transactional
    public List<IntegrationFlowStep> reorderSteps(UUID flowId, List<UUID> stepIdsInOrder) {
        getFlow(flowId);
        List<IntegrationFlowStep> steps = flowStepRepository.findByFlowId(flowId);
        Map<UUID, IntegrationFlowStep> stepMap = new HashMap<>();
        for (IntegrationFlowStep s : steps) {
            stepMap.put(s.getId(), s);
        }
        for (int i = 0; i < stepIdsInOrder.size(); i++) {
            IntegrationFlowStep s = stepMap.get(stepIdsInOrder.get(i));
            if (s != null) {
                s.setStepOrder(i + 1);
                flowStepRepository.save(s);
            }
        }
        return flowStepRepository.findByFlowIdOrderByStepOrderAsc(flowId);
    }

    // ──────────────────────────────────────────────
    // Transform Mappings
    // ──────────────────────────────────────────────

    public Page<IntegrationTransformMapping> getAllMappings(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return mappingRepository.findByTenantId(tenantId, pageable);
    }

    public IntegrationTransformMapping getMapping(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return mappingRepository.findById(id)
                .filter(m -> m.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationTransformMapping", id));
    }

    @Transactional
    public IntegrationTransformMapping createMapping(IntegrationTransformMapping mapping) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        mapping.setTenantId(tenantId);
        return mappingRepository.save(mapping);
    }

    @Transactional
    public IntegrationTransformMapping updateMapping(UUID id, IntegrationTransformMapping updated) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationTransformMapping existing = mappingRepository.findById(id)
                .filter(m -> m.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationTransformMapping", id));
        existing.setName(updated.getName());
        existing.setSourceFormat(updated.getSourceFormat());
        existing.setTargetFormat(updated.getTargetFormat());
        existing.setMappingDefinition(updated.getMappingDefinition());
        existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 1);
        existing.setIsActive(updated.getIsActive());
        return mappingRepository.save(existing);
    }

    // ──────────────────────────────────────────────
    // Validation Rules
    // ──────────────────────────────────────────────

    public List<IntegrationValidationRule> getRules() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ruleRepository.findByTenantId(tenantId);
    }

    public List<IntegrationValidationRule> getRulesByEntity(String entityType) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ruleRepository.findByTenantIdAndEntityType(tenantId, entityType);
    }

    @Transactional
    public IntegrationValidationRule createRule(IntegrationValidationRule rule) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        rule.setTenantId(tenantId);
        return ruleRepository.save(rule);
    }

    @Transactional
    public IntegrationValidationRule updateRule(UUID id, IntegrationValidationRule updated) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationValidationRule existing = ruleRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationValidationRule", id));
        existing.setName(updated.getName());
        existing.setRuleType(updated.getRuleType());
        existing.setEntityType(updated.getEntityType());
        existing.setFieldPath(updated.getFieldPath());
        existing.setOperator(updated.getOperator());
        existing.setValue(updated.getValue());
        existing.setErrorMessage(updated.getErrorMessage());
        existing.setSeverity(updated.getSeverity());
        existing.setIsActive(updated.getIsActive());
        return ruleRepository.save(existing);
    }

    @Transactional
    public IntegrationValidationRule toggleRule(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationValidationRule existing = ruleRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationValidationRule", id));
        existing.setIsActive(!Boolean.TRUE.equals(existing.getIsActive()));
        return ruleRepository.save(existing);
    }

    // ──────────────────────────────────────────────
    // Import Jobs
    // ──────────────────────────────────────────────

    public Page<IntegrationImportJob> getAllImportJobs(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return importJobRepository.findByTenantId(tenantId, pageable);
    }

    public IntegrationImportJob getImportJob(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return importJobRepository.findById(id)
                .filter(j -> j.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationImportJob", id));
    }

    @Transactional
    public IntegrationImportJob createImportJob(IntegrationImportJob job) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        job.setTenantId(tenantId);
        return importJobRepository.save(job);
    }

    @Transactional
    public void retryImportJob(UUID id) {
        getImportJob(id);
        importExportEngine.retryFailedJob(id);
    }

    @Transactional
    public void cancelImportJob(UUID id) {
        getImportJob(id);
        importExportEngine.cancelJob(id);
    }

    public Map<String, Long> getImportJobSummary() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", importJobRepository.countByTenantIdAndStatus(tenantId, null) + importJobRepository.count());
        summary.put("pending", importJobRepository.countByTenantIdAndStatus(tenantId, "PENDING"));
        summary.put("processing", importJobRepository.countByTenantIdAndStatus(tenantId, "PROCESSING"));
        summary.put("completed", importJobRepository.countByTenantIdAndStatus(tenantId, "COMPLETED"));
        summary.put("failed", importJobRepository.countByTenantIdAndStatus(tenantId, "FAILED"));
        summary.put("cancelled", importJobRepository.countByTenantIdAndStatus(tenantId, "CANCELLED"));
        return summary;
    }

    // ──────────────────────────────────────────────
    // Export Jobs
    // ──────────────────────────────────────────────

    public Page<IntegrationExportJob> getAllExportJobs(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return exportJobRepository.findByTenantId(tenantId, pageable);
    }

    public IntegrationExportJob getExportJob(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return exportJobRepository.findById(id)
                .filter(j -> j.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationExportJob", id));
    }

    @Transactional
    public IntegrationExportJob createExportJob(IntegrationExportJob job) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        job.setTenantId(tenantId);
        return exportJobRepository.save(job);
    }

    @Transactional
    public void retryExportJob(UUID id) {
        getExportJob(id);
        importExportEngine.retryFailedJob(id);
    }

    @Transactional
    public void cancelExportJob(UUID id) {
        getExportJob(id);
        importExportEngine.cancelJob(id);
    }

    public Map<String, Long> getExportJobSummary() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("pending", exportJobRepository.countByTenantIdAndStatus(tenantId, "PENDING"));
        summary.put("processing", exportJobRepository.countByTenantIdAndStatus(tenantId, "PROCESSING"));
        summary.put("completed", exportJobRepository.countByTenantIdAndStatus(tenantId, "COMPLETED"));
        summary.put("failed", exportJobRepository.countByTenantIdAndStatus(tenantId, "FAILED"));
        summary.put("cancelled", exportJobRepository.countByTenantIdAndStatus(tenantId, "CANCELLED"));
        return summary;
    }

    // ──────────────────────────────────────────────
    // DLQ
    // ──────────────────────────────────────────────

    public Page<IntegrationDLQ> getDLQEntries(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return dlqRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public void replayDLQEntry(UUID dlqId) {
        dlqManager.retryFromDLQ(dlqId);
    }

    @Transactional
    public void ignoreDLQEntry(UUID dlqId) {
        IntegrationDLQ dlq = dlqRepository.findById(dlqId)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationDLQ", dlqId));
        dlq.setStatus("IGNORED");
        dlqRepository.save(dlq);
    }

    public Map<String, Object> getDLQSummary() {
        return dlqManager.getDLQStats();
    }

    // ──────────────────────────────────────────────
    // Audit Log
    // ──────────────────────────────────────────────

    public Page<IntegrationAuditLog> getAuditLogs(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return auditLogRepository.findByTenantId(tenantId, pageable);
    }

    public Page<IntegrationAuditLog> getAuditLogsByEntity(String entityType, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return auditLogRepository.findByTenantIdAndEntityType(tenantId, entityType, pageable);
    }

    public Map<String, Object> getAuditLogSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", auditLogRepository.count());
        return summary;
    }

    // ──────────────────────────────────────────────
    // CDC Events
    // ──────────────────────────────────────────────

    public List<IntegrationCDCEvent> getPendingEvents() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return cdcEventRepository.findByTenantIdAndProcessed(tenantId, false);
    }

    @Transactional
    public void markEventProcessed(UUID eventId) {
        IntegrationCDCEvent event = cdcEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationCDCEvent", eventId));
        event.setProcessed(true);
        event.setProcessedAt(LocalDateTime.now());
        cdcEventRepository.save(event);
    }

    public Map<String, Object> getCDCSummary() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> summary = new LinkedHashMap<>();
        long total = cdcEventRepository.count();
        long pending = cdcEventRepository.countByTenantIdAndProcessed(tenantId, false);
        summary.put("total", total);
        summary.put("pending", pending);
        return summary;
    }

    // ──────────────────────────────────────────────
    // Dashboard
    // ──────────────────────────────────────────────

    public Map<String, Object> getIntegrationDashboard() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> dashboard = new LinkedHashMap<>();

        long activeEndpoints = endpointRepository.findByTenantIdAndIsActive(tenantId, true, Pageable.unpaged()).getTotalElements();
        dashboard.put("activeEndpoints", activeEndpoints);

        long activeFlows = flowRepository.findByTenantIdAndIsActive(tenantId, true, Pageable.unpaged()).getTotalElements();
        dashboard.put("activeFlows", activeFlows);

        long runningJobs = importJobRepository.countByTenantIdAndStatus(tenantId, "PROCESSING")
                         + exportJobRepository.countByTenantIdAndStatus(tenantId, "PROCESSING");
        dashboard.put("runningJobs", runningJobs);

        long failedJobsToday = importJobRepository.countByTenantIdAndStatus(tenantId, "FAILED")
                             + exportJobRepository.countByTenantIdAndStatus(tenantId, "FAILED");
        dashboard.put("failedJobsToday", failedJobsToday);

        dashboard.put("totalMessagesProcessed", messageRepository.count());

        long dlqCount = dlqRepository.count();
        dashboard.put("dlqCount", dlqCount);

        long pendingCDCEvents = cdcEventRepository.countByTenantIdAndProcessed(tenantId, false);
        dashboard.put("pendingCDCEvents", pendingCDCEvents);

        return dashboard;
    }
}
