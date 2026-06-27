package com.nexus.oms.service;

import com.nexus.oms.entity.IntegrationDLQ;
import com.nexus.oms.entity.IntegrationMessage;
import com.nexus.oms.repository.IntegrationDLQRepository;
import com.nexus.oms.repository.IntegrationMessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DLQManager {

    private final IntegrationDLQRepository dlqRepository;
    private final IntegrationMessageRepository messageRepository;

    public DLQManager(IntegrationDLQRepository dlqRepository,
                      IntegrationMessageRepository messageRepository) {
        this.dlqRepository = dlqRepository;
        this.messageRepository = messageRepository;
    }

    public IntegrationDLQ moveToDLQ(IntegrationMessage msg, String error, String category) {
        IntegrationDLQ dlq = IntegrationDLQ.builder()
                .tenantId(msg.getTenantId())
                .flowId(msg.getFlowId())
                .endpointId(null)
                .messageId(msg.getMessageId())
                .originalPayload(msg.getPayload())
                .errorMessage(error)
                .errorCategory(category)
                .retryCount(0)
                .maxRetries(msg.getMaxRetries() != null ? msg.getMaxRetries() : 3)
                .status("FAILED")
                .build();
        return dlqRepository.save(dlq);
    }

    public void retryFromDLQ(UUID dlqId) {
        IntegrationDLQ dlq = dlqRepository.findById(dlqId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ entry not found: " + dlqId));

        IntegrationMessage msg = IntegrationMessage.builder()
                .tenantId(dlq.getTenantId())
                .flowId(dlq.getFlowId())
                .messageId(dlq.getMessageId() != null ? dlq.getMessageId() : UUID.randomUUID().toString())
                .payload(dlq.getOriginalPayload())
                .status("PENDING")
                .retryCount(0)
                .maxRetries(dlq.getMaxRetries())
                .build();
        messageRepository.save(msg);

        dlq.setStatus("REPLAYED");
        dlq.setLastRetryAt(LocalDateTime.now());
        dlqRepository.save(dlq);
    }

    public int replayAllByFlow(UUID flowId) {
        List<IntegrationDLQ> entries = dlqRepository.findByFlowId(flowId);
        int count = 0;
        for (IntegrationDLQ dlq : entries) {
            if ("FAILED".equals(dlq.getStatus())) {
                retryFromDLQ(dlq.getId());
                count++;
            }
        }
        return count;
    }

    public Map<String, Object> getDLQStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", dlqRepository.count());
        stats.put("failed", dlqRepository.findByStatus("FAILED").size());
        stats.put("replayed", dlqRepository.findByStatus("REPLAYED").size());
        return stats;
    }
}
