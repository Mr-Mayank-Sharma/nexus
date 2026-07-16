package com.nexus.oms.kafka;

import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final AuditLogRepository auditLogRepository;

    public KafkaConsumerService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(String message) {
        processOrderEvent(message, "ORDER", "CREATED");
    }

    @KafkaListener(topics = "order.confirmed")
    public void handleOrderConfirmed(String message) {
        processOrderEvent(message, "ORDER", "CONFIRMED");
    }

    @KafkaListener(topics = "order.allocated")
    public void handleOrderAllocated(String message) {
        processOrderEvent(message, "ORDER", "ALLOCATED");
    }

    @KafkaListener(topics = "order.shipped")
    public void handleOrderShipped(String message) {
        processOrderEvent(message, "ORDER", "SHIPPED");
    }

    @KafkaListener(topics = "order.delivered")
    public void handleOrderDelivered(String message) {
        processOrderEvent(message, "ORDER", "DELIVERED");
    }

    @KafkaListener(topics = "order.cancelled")
    public void handleOrderCancelled(String message) {
        processOrderEvent(message, "ORDER", "CANCELLED");
    }

    private void processOrderEvent(String message, String entityType, String eventType) {
        log.info("Processing {} event for {}: {}", eventType, entityType, message);
        try {
            auditLogRepository.save(NxAuditLog.builder()
                    .entityId(UUID.fromString(message))
                    .entityType(entityType)
                    .eventType(eventType)
                    .build());
        } catch (Exception e) {
            log.error("Error processing {} event: {}", eventType, e.getMessage(), e);
            throw e;
        }
    }
}
