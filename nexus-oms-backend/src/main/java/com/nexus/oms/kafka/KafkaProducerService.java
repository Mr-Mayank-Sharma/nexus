package com.nexus.oms.kafka;

import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "nexus.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final OutboxWriter outbox;

    public KafkaProducerService(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * Records the event in the transactional outbox; {@link OutboxRelay} delivers it to Kafka
     * after the surrounding transaction commits. Never blocks on Kafka.
     */
    public void publish(String topic, String message) {
        publish(topic, null, message);
    }

    public void publish(String topic, String key, String message) {
        UUID id = outbox.enqueue(topic, key, message);
        log.debug("Queued event {} for topic {} (key {})", id, topic, key);
    }

    public void publishEvent(String topic, String eventType, String data) {
        UUID tenantId;
        try {
            tenantId = TenantContext.getCurrentTenantId();
        } catch (IllegalStateException e) {
            tenantId = null;
        }
        EventRecord event = EventRecord.create(eventType, topic, data, tenantId);
        String json = event.toJson();
        publish(topic, event.eventId(), json);
    }

    public void publishEvent(String topic, String eventType, String data, UUID tenantId) {
        EventRecord event = EventRecord.create(eventType, topic, data, tenantId);
        String json = event.toJson();
        publish(topic, event.eventId(), json);
    }
}
