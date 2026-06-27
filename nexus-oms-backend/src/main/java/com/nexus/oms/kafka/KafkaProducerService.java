package com.nexus.oms.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String message) {
        log.info("Publishing event to topic {}: {}", topic, message);
        CompletableFuture<?> future = kafkaTemplate.send(topic, message);
        future.exceptionally(ex -> {
            log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage(), ex);
            return null;
        });
    }

    public void publish(String topic, String key, String message) {
        log.info("Publishing event to topic {} with key {}: {}", topic, key, message);
        CompletableFuture<?> future = kafkaTemplate.send(topic, key, message);
        future.exceptionally(ex -> {
            log.error("Failed to publish event to topic {} with key {}: {}", topic, key, ex.getMessage(), ex);
            return null;
        });
    }
}
