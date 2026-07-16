package com.nexus.oms.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private static final long PUBLISH_TIMEOUT_MS = 30000;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String message) {
        log.info("Publishing event to topic {}: {}", topic, message);
        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, message);
            future.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to publish event to topic {}: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Kafka publish to " + topic + " failed", e);
        }
    }

    public void publish(String topic, String key, String message) {
        log.info("Publishing event to topic {} with key {}: {}", topic, key, message);
        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, message);
            future.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to publish event to topic {} with key {}: {}", topic, key, e.getMessage(), e);
            throw new RuntimeException("Kafka publish to " + topic + " failed", e);
        }
    }
}
