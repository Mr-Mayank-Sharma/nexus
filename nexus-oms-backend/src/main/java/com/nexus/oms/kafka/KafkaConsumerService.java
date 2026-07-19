package com.nexus.oms.kafka;

import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "nexus.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaConsumerService(AuditLogRepository auditLogRepository,
                                KafkaTemplate<String, String> kafkaTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void handleOrderCreated(String message) {
        processOrderEvent(message, "ORDER", "CREATED", Topics.ORDER_CREATED);
    }

    @KafkaListener(topics = Topics.ORDER_CONFIRMED)
    public void handleOrderConfirmed(String message) {
        processOrderEvent(message, "ORDER", "CONFIRMED", Topics.ORDER_CONFIRMED);
    }

    @KafkaListener(topics = Topics.ORDER_ALLOCATED)
    public void handleOrderAllocated(String message) {
        processOrderEvent(message, "ORDER", "ALLOCATED", Topics.ORDER_ALLOCATED);
    }

    @KafkaListener(topics = Topics.ORDER_SHIPPED)
    public void handleOrderShipped(String message) {
        processOrderEvent(message, "ORDER", "SHIPPED", Topics.ORDER_SHIPPED);
    }

    @KafkaListener(topics = Topics.ORDER_DELIVERED)
    public void handleOrderDelivered(String message) {
        processOrderEvent(message, "ORDER", "DELIVERED", Topics.ORDER_DELIVERED);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void handleOrderCancelled(String message) {
        processOrderEvent(message, "ORDER", "CANCELLED", Topics.ORDER_CANCELLED);
    }

    @KafkaListener(topics = Topics.ORDER_UPDATED)
    public void handleOrderUpdated(String message) {
        processOrderEvent(message, "ORDER", "UPDATED", Topics.ORDER_UPDATED);
    }

    @KafkaListener(topics = Topics.SHIPMENT_CREATED)
    public void handleShipmentCreated(String message) {
        processOrderEvent(message, "SHIPMENT", "CREATED", Topics.SHIPMENT_CREATED);
    }

    @KafkaListener(topics = Topics.SHIPMENT_UPDATED)
    public void handleShipmentUpdated(String message) {
        processOrderEvent(message, "SHIPMENT", "UPDATED", Topics.SHIPMENT_UPDATED);
    }

    @KafkaListener(topics = Topics.INVENTORY_ADJUSTED)
    public void handleInventoryAdjusted(String message) {
        processOrderEvent(message, "INVENTORY", "ADJUSTED", Topics.INVENTORY_ADJUSTED);
    }

    @KafkaListener(topics = Topics.PAYMENT_RECEIVED)
    public void handlePaymentReceived(String message) {
        processOrderEvent(message, "PAYMENT", "RECEIVED", Topics.PAYMENT_RECEIVED);
    }

    @KafkaListener(topics = Topics.PAYMENT_REFUNDED)
    public void handlePaymentRefunded(String message) {
        processOrderEvent(message, "PAYMENT", "REFUNDED", Topics.PAYMENT_REFUNDED);
    }

    @KafkaListener(topics = Topics.RETURN_CREATED)
    public void handleReturnCreated(String message) {
        processOrderEvent(message, "RETURN", "CREATED", Topics.RETURN_CREATED);
    }

    @KafkaListener(topics = Topics.RETURN_APPROVED)
    public void handleReturnApproved(String message) {
        processOrderEvent(message, "RETURN", "APPROVED", Topics.RETURN_APPROVED);
    }

    @KafkaListener(topicPattern = ".*\\.dlq")
    public void handleDlq(String message) {
        log.warn("Received DLQ event: {}", message);
    }

    private void processOrderEvent(String message, String entityType, String eventType, String topic) {
        log.info("Processing {} event for {}: {}", eventType, entityType, message);
        try {
            EventRecord event = EventRecord.fromJson(message);
            String entityId = event.data();
            auditLogRepository.save(NxAuditLog.builder()
                    .entityId(entityId != null ? UUID.fromString(entityId) : UUID.fromString(event.eventId()))
                    .entityType(entityType)
                    .eventType(eventType)
                    .build());
        } catch (Exception e) {
            log.error("Error processing {} event on {}: {}", eventType, topic, e.getMessage(), e);
            try {
                kafkaTemplate.send(Topics.dlq(topic), message);
            } catch (Exception dlqEx) {
                log.error("Failed to send event to DLQ {}: {}", Topics.dlq(topic), dlqEx.getMessage());
            }
        }
    }
}
