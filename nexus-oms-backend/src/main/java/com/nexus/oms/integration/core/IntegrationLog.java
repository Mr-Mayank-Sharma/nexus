package com.nexus.oms.integration.core;

import com.nexus.oms.entity.NxSyncLog;
import com.nexus.oms.repository.NxSyncLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IntegrationLog {

    private static final Logger log = LoggerFactory.getLogger(IntegrationLog.class);

    private final NxSyncLogRepository syncLogRepository;

    public IntegrationLog(NxSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    public NxSyncLog startSync(UUID tenantId, String integrationType, String syncType) {
        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType(integrationType)
                .syncType(syncType)
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);
        log.info("Sync started: tenant={} type={} sync={}", tenantId, integrationType, syncType);
        return syncLog;
    }

    public void completeSync(NxSyncLog syncLog, String status, int succeeded, int failed, String message) {
        syncLog.setStatus(status);
        syncLog.setItemsSucceeded(succeeded);
        syncLog.setItemsFailed(failed);
        syncLog.setMessage(message);
        syncLogRepository.save(syncLog);
        log.info("Sync completed: status={} succeeded={} failed={}", status, succeeded, failed);
    }

    public void logError(UUID tenantId, String integrationType, String syncType, String error) {
        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType(integrationType)
                .syncType(syncType)
                .status("FAILED")
                .message(error)
                .build();
        syncLogRepository.save(syncLog);
        log.error("Sync error: tenant={} type={} sync={} error={}", tenantId, integrationType, syncType, error);
    }
}
