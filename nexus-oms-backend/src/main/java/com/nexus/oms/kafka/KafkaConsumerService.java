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
        try {
            log.info("Order created event received: {}", message);
                    auditLogRepository.save(NxAuditLog.builder()
                    .entityId(UUID.fromString(message))
                    .entityType("ORDER")
                    .eventType("CREATED")
                    .build());
        } catch (Exception e) {
            log.error("Error processing order.created event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.confirmed")
    public void handleOrderConfirmed(String message) {
        try {
            log.info("Order confirmed event received: {}", message);
            auditLogRepository.save(NxAuditLog.builder()
                    .entityId(UUID.fromString(message))
                    .entityType("ORDER")
                    .eventType("CONFIRMED")
                    .build());
        } catch (Exception e) {
            log.error("Error processing order.confirmed event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.allocated")
    public void handleOrderAllocated(String message) {
        try {
            log.info("Order allocated event received: {}", message);
            auditLogRepository.save(NxAuditLog.builder()
                    .entityId(UUID.fromString(message))
                    .entityType("ORDER")
                    .eventType("ALLOCATED")
                    .build());
        } catch (Exception e) {
            log.error("Error processing order.allocated event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.shipped")
    public void handleOrderShipped(String message) {
        try {
            log.info("Order shipped event received: {}", message);
            auditLogRepository.save(NxAuditLog.builder()
                    .entityId(UUID.fromString(message))
                    .entityType("ORDER")
                    .eventType("SHIPPED")
                    .build());
        } catch (Exception e) {
            log.error("Error processing order.shipped event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.delivered")
    public void handleOrderDelivered(String message) {
        try {
            log.info("Order delivered event received: {}", message);
            auditLogRepository.save(NxAuditLog.builder()
                    .entityId(UUID.fromString(message))
                    .entityType("ORDER")
                    .eventType("DELIVERED")
                    .build());
        } catch (Exception e) {
            log.error("Error processing order.delivered event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.cancelled")
    public void handleOrderCancelled(String message) {
        try {
            log.info("Order cancelled event received: {}", message);
            auditLogRepository.save(NxAuditLog.builder()
                    .entityId(UUID.fromString(message))
                    .entityType("ORDER")
                    .eventType("CANCELLED")
                    .build());
        } catch (Exception e) {
            log.error("Error processing order.cancelled event: {}", e.getMessage(), e);
        }
    }
}
