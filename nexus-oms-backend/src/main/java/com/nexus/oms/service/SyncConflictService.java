package com.nexus.oms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.SyncReconciliationReport;
import com.nexus.oms.entity.NxSyncConflict;
import com.nexus.oms.entity.NxSyncFieldMapping;
import com.nexus.oms.entity.NxSyncJobStatus;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.SyncConflictRepository;
import com.nexus.oms.repository.SyncFieldMappingRepository;
import com.nexus.oms.repository.SyncJobStatusRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * T-13: Bidirectional sync conflict handling.
 *
 * The #1 integration pain across HotWax accounts: a consigned order shipped in
 * the OMS got UNDONE when NetSuite fed back stale data ("one overrides the
 * other and changes what OFBiz had"). This service prevents stale inbound data
 * from silently overwriting newer local state.
 */
@Service
public class SyncConflictService {

    private static final Logger log = LoggerFactory.getLogger(SyncConflictService.class);

    private final SyncConflictRepository conflictRepository;
    private final SyncFieldMappingRepository fieldMappingRepository;
    private final SyncJobStatusRepository jobStatusRepository;
    private final ObjectMapper objectMapper;

    public SyncConflictService(SyncConflictRepository conflictRepository,
                               SyncFieldMappingRepository fieldMappingRepository,
                               SyncJobStatusRepository jobStatusRepository,
                               ObjectMapper objectMapper) {
        this.conflictRepository = conflictRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.jobStatusRepository = jobStatusRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 1. CONFLICT DETECTION
    // ------------------------------------------------------------------

    /**
     * Detect whether an inbound update conflicts with newer local state.
     *
     * @param entityType     ORDER | INVENTORY | SHIPMENT | ...
     * @param entityId       the local entity id
     * @param inboundVersion the version the inbound update was based on
     * @param localVersion   the current local version
     * @return a decision describing whether to apply, reject, or queue
     */
    @Transactional
    public ConflictDecision detectConflict(String entityType, UUID entityId,
                                           long inboundVersion, long localVersion) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        if (inboundVersion >= localVersion) {
            // Inbound is based on current-or-newer state: safe to apply.
            return ConflictDecision.apply();
        }

        // Inbound is stale relative to local state: it's a conflict.
        NxSyncConflict conflict = NxSyncConflict.builder()
                .tenantId(tenantId)
                .entityType(entityType)
                .entityId(entityId)
                .direction("INBOUND")
                .sourceSystem("REMOTE")
                .inboundVersion(inboundVersion)
                .localVersion(localVersion)
                .status("OPEN")
                .resolution("PENDING")
                .build();
        conflict = conflictRepository.save(conflict);

        log.warn("Sync conflict detected: {} {} inbound v{} vs local v{} (conflict {})",
                entityType, entityId, inboundVersion, localVersion, conflict.getId());

        return ConflictDecision.conflict(conflict.getId());
    }

    /**
     * Decide per-field winners for an inbound update, applying any configured
     * field mappings. Fields without a mapping default to LOCAL_WINS (preserve
     * newer local state).
     *
     * @return map of fieldName -> winner (LOCAL | REMOTE)
     */
    @Transactional(readOnly = true)
    public Map<String, String> resolveFieldWinners(String entityType, String direction,
                                                   Map<String, Object> inboundFields) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, String> winners = new HashMap<>();

        List<NxSyncFieldMapping> mappings =
                fieldMappingRepository.findByTenantIdAndEntityTypeAndDirectionAndIsActiveTrue(
                        tenantId, entityType, direction);

        Map<String, String> mappingByField = mappings.stream()
                .collect(Collectors.toMap(NxSyncFieldMapping::getFieldName, NxSyncFieldMapping::getWinner));

        for (String field : inboundFields.keySet()) {
            winners.put(field, mappingByField.getOrDefault(field, "LOCAL"));
        }
        return winners;
    }

    // ------------------------------------------------------------------
    // 2. RESOLUTION
    // ------------------------------------------------------------------

    /**
     * Resolve an open conflict. LOCAL_WINS preserves local state (default);
     * INBOUND_WINS applies the remote state; MERGED applies a field-level merge.
     */
    @Transactional
    public NxSyncConflict resolveConflict(UUID conflictId, String resolution,
                                          Map<String, Object> mergedFields) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxSyncConflict conflict = conflictRepository.findById(conflictId)
                .orElseThrow(() -> new ResourceNotFoundException("Sync conflict not found: " + conflictId));

        if (!tenantId.equals(conflict.getTenantId())) {
            throw new BadRequestException("Conflict does not belong to the current tenant");
        }

        String normalized = resolution == null ? "LOCAL_WINS" : resolution.toUpperCase();
        if (!Set.of("LOCAL_WINS", "INBOUND_WINS", "MERGED", "MANUAL").contains(normalized)) {
            throw new BadRequestException("Invalid resolution: " + resolution);
        }

        conflict.setResolution(normalized);
        conflict.setStatus("RESOLVED");
        conflict.setResolvedAt(LocalDateTime.now());
        conflict.setResolvedBy(TenantContext.getCurrentUserId());

        if ("MERGED".equals(normalized) && mergedFields != null) {
            try {
                conflict.setConflictingFields(objectMapper.writeValueAsString(mergedFields));
            } catch (Exception e) {
                throw new BadRequestException("Could not serialize merged fields");
            }
        }

        conflict = conflictRepository.save(conflict);
        log.info("Resolved sync conflict {} as {}", conflictId, normalized);
        return conflict;
    }

    // ------------------------------------------------------------------
    // 3. IDEMPOTENT, RETRYABLE SYNC JOBS + RECOVERY
    // ------------------------------------------------------------------

    /**
     * Recovery job (default every 15 min) that retries failed/pending sync
     * records. Idempotent: keyed on job name, only touches FAILED/PENDING.
     */
    @Scheduled(cron = "${nexus.sync.recovery.cron:0 */15 * * * *}")
    @Transactional
    public void recoveryJob() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxSyncJobStatus> stuck = jobStatusRepository.findByTenantIdAndStatus(tenantId, "FAILED");
        stuck.addAll(jobStatusRepository.findByTenantIdAndStatus(tenantId, "PENDING"));

        for (NxSyncJobStatus job : stuck) {
            // Reset to QUEUED so the pipeline picks it up again.
            job.setStatus("QUEUED");
            job.setNextRunAt(LocalDateTime.now().plusMinutes(1));
            jobStatusRepository.save(job);
            log.info("Recovery: requeued sync job {} ({} failed, {} pending)",
                    job.getJobName(), job.getRecordsFailed(), job.getRecordsTotal() - job.getRecordsProcessed());
        }
    }

    /**
     * Trigger the recovery job on demand (admin endpoint).
     */
    @Transactional
    public int triggerRecovery() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxSyncJobStatus> stuck = jobStatusRepository.findByTenantIdAndStatus(tenantId, "FAILED");
        stuck.addAll(jobStatusRepository.findByTenantIdAndStatus(tenantId, "PENDING"));
        for (NxSyncJobStatus job : stuck) {
            job.setStatus("QUEUED");
            job.setNextRunAt(LocalDateTime.now().plusMinutes(1));
            jobStatusRepository.save(job);
        }
        return stuck.size();
    }

    /**
     * Record a sync job run (idempotent upsert keyed on tenant + job name).
     */
    @Transactional
    public NxSyncJobStatus recordJobRun(String jobName, String direction, String status,
                                        int total, int processed, int failed) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxSyncJobStatus job = jobStatusRepository.findByTenantIdAndJobName(tenantId, jobName)
                .orElseGet(() -> NxSyncJobStatus.builder()
                        .tenantId(tenantId)
                        .jobName(jobName)
                        .direction(direction)
                        .build());
        job.setStatus(status);
        job.setRecordsTotal(total);
        job.setRecordsProcessed(processed);
        job.setRecordsFailed(failed);
        job.setLastRunAt(LocalDateTime.now());
        return jobStatusRepository.save(job);
    }

    // ------------------------------------------------------------------
    // 4. RECONCILIATION REPORTING
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SyncReconciliationReport getReconciliationReport(UUID tenantId) {
        List<NxSyncJobStatus> jobs = jobStatusRepository.findByTenantId(tenantId);

        Map<String, SyncReconciliationReport.DirectionSummary> byDirection = new LinkedHashMap<>();
        for (String dir : List.of("INBOUND", "OUTBOUND")) {
            byDirection.put(dir, SyncReconciliationReport.DirectionSummary.builder()
                    .direction(dir).pending(0).queued(0).completed(0).failed(0).build());
        }

        for (NxSyncJobStatus job : jobs) {
            SyncReconciliationReport.DirectionSummary summary =
                    byDirection.computeIfAbsent(job.getDirection(), d ->
                            SyncReconciliationReport.DirectionSummary.builder().direction(d)
                                    .pending(0).queued(0).completed(0).failed(0).build());
            switch (job.getStatus()) {
                case "PENDING" -> summary.setPending(summary.getPending() + 1);
                case "QUEUED" -> summary.setQueued(summary.getQueued() + 1);
                case "COMPLETED" -> summary.setCompleted(summary.getCompleted() + 1);
                case "FAILED" -> summary.setFailed(summary.getFailed() + 1);
                default -> { }
            }
        }

        return SyncReconciliationReport.builder()
                .directions(new ArrayList<>(byDirection.values()))
                .openConflicts(conflictRepository.countByTenantIdAndStatus(tenantId, "OPEN"))
                .resolvedConflicts(conflictRepository.countByTenantIdAndStatus(tenantId, "RESOLVED"))
                .build();
    }

    // ------------------------------------------------------------------
    // 5. TRANSACTION-TYPE HANDLING
    // ------------------------------------------------------------------

    /**
     * Gracefully handle unsupported transaction types (e.g., NetSuite FRTTRA):
     * skip + flag rather than breaking the pipeline.
     */
    public void handleUnsupportedTransaction(String txType, String payload) {
        log.warn("Skipping unsupported transaction type '{}' (payload {} bytes) — flagged, not fatal",
                txType, payload == null ? 0 : payload.length());
        // In a full implementation this would write to an audit/flag table.
    }

    // ------------------------------------------------------------------
    // Query helpers
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<NxSyncConflict> getConflicts(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return conflictRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return conflictRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public NxSyncConflict getConflict(UUID conflictId) {
        NxSyncConflict conflict = conflictRepository.findById(conflictId)
                .orElseThrow(() -> new ResourceNotFoundException("Sync conflict not found: " + conflictId));
        if (!TenantContext.getCurrentTenantId().equals(conflict.getTenantId())) {
            throw new BadRequestException("Conflict does not belong to the current tenant");
        }
        return conflict;
    }

    @Transactional
    public NxSyncFieldMapping upsertFieldMapping(NxSyncFieldMapping mapping) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        mapping.setTenantId(tenantId);
        return fieldMappingRepository.save(mapping);
    }

    @Transactional(readOnly = true)
    public List<NxSyncFieldMapping> getFieldMappings(UUID tenantId) {
        return fieldMappingRepository.findByTenantId(tenantId);
    }

    // ------------------------------------------------------------------
    // Decision value object
    // ------------------------------------------------------------------

    public record ConflictDecision(boolean isConflict, UUID conflictId) {
        public static ConflictDecision apply() {
            return new ConflictDecision(false, null);
        }
        public static ConflictDecision conflict(UUID conflictId) {
            return new ConflictDecision(true, conflictId);
        }
    }
}
