package com.nexus.oms.config;

import com.nexus.oms.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "nexus.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICATION = 1;

    @Bean
    public List<NewTopic> omsTopics() {
        return List.of(
                topic(Topics.ORDER_CREATED),
                topic(Topics.ORDER_UPDATED),
                topic(Topics.ORDER_CANCELLED),
                topic(Topics.ORDER_CONFIRMED),
                topic(Topics.ORDER_ALLOCATED),
                topic(Topics.ORDER_SHIPPED),
                topic(Topics.ORDER_DELIVERED),
                topic(Topics.SHIPMENT_CREATED),
                topic(Topics.SHIPMENT_UPDATED),
                topic(Topics.INVENTORY_ADJUSTED),
                topic(Topics.PAYMENT_RECEIVED),
                topic(Topics.PAYMENT_REFUNDED),
                topic(Topics.RETURN_CREATED),
                topic(Topics.RETURN_APPROVED),
                dlqTopic(Topics.ORDER_CREATED),
                dlqTopic(Topics.ORDER_UPDATED),
                dlqTopic(Topics.ORDER_CANCELLED),
                dlqTopic(Topics.SHIPMENT_CREATED),
                dlqTopic(Topics.SHIPMENT_UPDATED),
                dlqTopic(Topics.INVENTORY_ADJUSTED),
                dlqTopic(Topics.PAYMENT_RECEIVED),
                dlqTopic(Topics.PAYMENT_REFUNDED),
                dlqTopic(Topics.RETURN_CREATED),
                dlqTopic(Topics.RETURN_APPROVED)
        );
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    private static NewTopic dlqTopic(String sourceTopic) {
        return TopicBuilder.name(Topics.dlq(sourceTopic))
                .partitions(1)
                .replicas(REPLICATION)
                .build();
    }
}
