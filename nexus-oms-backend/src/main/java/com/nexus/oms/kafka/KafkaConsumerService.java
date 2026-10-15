package com.nexus.oms.kafka;

import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.entity.NxProcessedEvent;
import com.nexus.oms.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import com.nexus.oms.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "nexus.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final AuditLogRepository auditLogRepository;
    private final ProcessedEventRepository processedEvents;

    public KafkaConsumerService(AuditLogRepository auditLogRepository, ProcessedEventRepository processedEvents) {
        this.auditLogRepository = auditLogRepository;
        this.processedEvents = processedEvents;
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

    /** Event ids only matter while Kafka can still redeliver; keep two weeks (default topic retention is one). */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 45 2 * * *")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "KafkaConsumerService.purgeProcessedEvents", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void purgeProcessedEvents() {
        int removed = processedEvents.deleteOlderThan(java.time.LocalDateTime.now().minusDays(14));
        log.info("Removed {} processed-event ids older than 14 days", removed);
    }

    @KafkaListener(topicPattern = ".*\\.dlq")
    public void handleDlq(String message) {
        log.warn("Received DLQ event: {}", message);
    }

    /**
     * Handles one event. Failures are thrown, not swallowed: the container's error handler retries
     * transient ones with a short backoff and then dead-letters the record (see KafkaConfig). A message
     * that cannot be parsed is rejected at once so it never blocks the partition behind it.
     */
    private void processOrderEvent(String message, String entityType, String eventType, String topic) {
        EventRecord event;
        UUID entityId;
        try {
            event = EventRecord.fromJson(message);
            entityId = UUID.fromString(event.data() != null ? event.data() : event.eventId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unreadable " + topic + " event: " + e.getMessage(), e);
        }
        // Delivery is at least once: claim the event id first; a second delivery finds it taken.
        if (event.eventId() != null) {
            if (processedEvents.existsById(event.eventId())) {
                log.debug("Skipping already-processed event {} on {}", event.eventId(), topic);
                return;
            }
            try {
                processedEvents.saveAndFlush(new NxProcessedEvent(event.eventId(), topic));
            } catch (DataIntegrityViolationException raced) {
                return;   // another instance claimed it between the check and the insert
            }
        }
        try {
            auditLogRepository.save(NxAuditLog.builder()
                    .tenantId(event.tenantId())
                    .entityId(entityId)
                    .entityType(entityType)
                    .eventType(eventType)
                    .build());
        } catch (RuntimeException e) {
            if (event.eventId() != null) processedEvents.deleteById(event.eventId());   // let the retry run
            throw e;
        }
    }
}
