package com.nexus.oms.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "nexus.kafka.enabled", havingValue = "false")
public class NoOpKafkaProducerService extends KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(NoOpKafkaProducerService.class);

    public NoOpKafkaProducerService() {
        super(null);
    }

    @Override
    public void publish(String topic, String message) {
        log.debug("Kafka disabled — skipping publish to topic {}", topic);
    }

    @Override
    public void publish(String topic, String key, String message) {
        log.debug("Kafka disabled — skipping publish to topic {}", topic);
    }

    @Override
    public void publishEvent(String topic, String eventType, String data) {
        log.debug("Kafka disabled — skipping event {} to topic {}", eventType, topic);
    }

    @Override
    public void publishEvent(String topic, String eventType, String data, UUID tenantId) {
        log.debug("Kafka disabled — skipping event {} to topic {}", eventType, topic);
    }
}
