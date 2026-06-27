package com.nexus.oms.integration.batch;

import com.nexus.oms.integration.core.Connector;
import com.nexus.oms.integration.core.ConnectorRegistry;
import com.nexus.oms.integration.core.IntegrationLog;
import com.nexus.oms.integration.dto.IntegrationEvent;
import com.nexus.oms.integration.dto.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BatchJobService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobService.class);

    private final ConnectorRegistry registry;
    private final IntegrationLog integrationLog;
    private final Map<String, BatchJob> activeJobs = new ConcurrentHashMap<>();

    public BatchJobService(ConnectorRegistry registry, IntegrationLog integrationLog) {
        this.registry = registry;
        this.integrationLog = integrationLog;
    }

    public BatchJob submitJob(String connectorId, String syncType, UUID tenantId, Map<String, Object> params) {
        Connector connector = registry.getConnector(connectorId);
        String jobId = UUID.randomUUID().toString();
        BatchJob job = new BatchJob(jobId, connectorId, syncType, tenantId, params);
        activeJobs.put(jobId, job);

        try {
            integrationLog.startSync(tenantId, connector.getMetadata().getName(), syncType);
            SyncResult result = connector.executeSync(syncType, tenantId, params);

            job.setStatus(result.getStatus().name());
            job.setItemsSucceeded(result.getItemsSucceeded());
            job.setItemsFailed(result.getItemsFailed());
            job.setCompletedAt(LocalDateTime.now());
            job.setDurationMs(result.getDurationMs());

            integrationLog.completeSync(
                    integrationLog.startSync(tenantId, connector.getMetadata().getName(), syncType),
                    result.getStatus().name(), result.getItemsSucceeded(), result.getItemsFailed(),
                    result.getErrors().isEmpty() ? "Completed" : String.join("; ", result.getErrors()));

            log.info("Batch job {} completed: {} succeeded, {} failed", jobId, result.getItemsSucceeded(), result.getItemsFailed());
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setError(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            log.error("Batch job {} failed", jobId, e);
        }

        return job;
    }

    public BatchJob getJob(String jobId) { return activeJobs.get(jobId); }
    public List<BatchJob> getActiveJobs() { return List.copyOf(activeJobs.values()); }
    public List<BatchJob> getJobsByConnector(String connectorId) {
        return activeJobs.values().stream()
                .filter(j -> j.getConnectorId().equals(connectorId))
                .toList();
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupOldJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        activeJobs.values().removeIf(j -> j.getCompletedAt() != null && j.getCompletedAt().isBefore(cutoff));
    }

    public static class BatchJob {
        private final String jobId;
        private final String connectorId;
        private final String syncType;
        private final UUID tenantId;
        private final Map<String, Object> params;
        private String status = "PENDING";
        private int itemsSucceeded;
        private int itemsFailed;
        private String error;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private long durationMs;

        public BatchJob(String jobId, String connectorId, String syncType, UUID tenantId, Map<String, Object> params) {
            this.jobId = jobId;
            this.connectorId = connectorId;
            this.syncType = syncType;
            this.tenantId = tenantId;
            this.params = params;
            this.startedAt = LocalDateTime.now();
        }

        public String getJobId() { return jobId; }
        public String getConnectorId() { return connectorId; }
        public String getSyncType() { return syncType; }
        public UUID getTenantId() { return tenantId; }
        public Map<String, Object> getParams() { return params; }
        public String getStatus() { return status; }
        public void setStatus(String s) { this.status = s; }
        public int getItemsSucceeded() { return itemsSucceeded; }
        public void setItemsSucceeded(int i) { this.itemsSucceeded = i; }
        public int getItemsFailed() { return itemsFailed; }
        public void setItemsFailed(int i) { this.itemsFailed = i; }
        public String getError() { return error; }
        public void setError(String e) { this.error = e; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime d) { this.completedAt = d; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long d) { this.durationMs = d; }
    }
}
