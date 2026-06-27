package com.nexus.oms.service;

import com.nexus.oms.entity.IntegrationCDCEvent;
import com.nexus.oms.repository.IntegrationCDCEventRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CDCProcessor {

    private final IntegrationCDCEventRepository cdcEventRepository;

    public CDCProcessor(IntegrationCDCEventRepository cdcEventRepository) {
        this.cdcEventRepository = cdcEventRepository;
    }

    public IntegrationCDCEvent captureEvent(String source, String entityType, UUID entityId,
                                            String eventType, String before, String after) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        IntegrationCDCEvent event = IntegrationCDCEvent.builder()
                .tenantId(tenantId)
                .source(source)
                .entityType(entityType)
                .entityId(entityId)
                .eventType(eventType)
                .beforeSnapshot(before)
                .afterSnapshot(after)
                .processed(false)
                .build();
        return cdcEventRepository.save(event);
    }

    public void processPendingEvents() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<IntegrationCDCEvent> pending = cdcEventRepository.findByTenantIdAndProcessed(tenantId, false);
        for (IntegrationCDCEvent event : pending) {
            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            cdcEventRepository.save(event);
        }
    }

    public long getUnprocessedCount() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return cdcEventRepository.countByTenantIdAndProcessed(tenantId, false);
    }
}
