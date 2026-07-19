package com.nexus.oms.integration.core;

import com.nexus.oms.integration.dto.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final List<EventHandler> handlers = new CopyOnWriteArrayList<>();
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean kafkaEnabled;

    public EventBus(java.util.Optional<KafkaTemplate<String, String>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate.orElse(null);
        this.kafkaEnabled = this.kafkaTemplate != null;
    }

    public void registerHandler(EventHandler handler) {
        handlers.add(handler);
    }

    public void publish(IntegrationEvent event) {
        log.info("Publishing event: type={} source={}", event.getEventType(), event.getSource());

        for (EventHandler handler : handlers) {
            try {
                if (handler.canHandle(event)) {
                    handler.handle(event);
                }
            } catch (Exception e) {
                log.error("Handler {} failed for event {}", handler.getClass().getSimpleName(), event.getEventId(), e);
            }
        }

        if (kafkaEnabled) {
            try {
                String topic = "integration." + event.getEventType().toLowerCase().replace("_", ".");
                kafkaTemplate.send(topic, event.getEventId(), event.toJson());
            } catch (Exception e) {
                log.error("Failed to publish event to Kafka", e);
            }
        }
    }

    public void publishBatch(List<IntegrationEvent> events) {
        events.forEach(this::publish);
    }

    public interface EventHandler {
        boolean canHandle(IntegrationEvent event);
        void handle(IntegrationEvent event);
    }
}
