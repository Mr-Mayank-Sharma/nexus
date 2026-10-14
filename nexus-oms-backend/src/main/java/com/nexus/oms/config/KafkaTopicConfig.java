package com.nexus.oms.config;

import com.nexus.oms.domain.OrderStatusPolicy;
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

    /** Replication factor; keep 1 for a single broker and set it to 3 in production. */
    @org.springframework.beans.factory.annotation.Value("${nexus.kafka.replication:1}")
    private short replication = 1;

    /**
     * Every topic that is published or consumed, plus a dead-letter topic for each consumed one. A
     * missing dead-letter topic made the failure path itself fail where brokers do not auto-create.
     */
    @Bean
    public List<NewTopic> omsTopics() {
        java.util.Set<String> names = new java.util.TreeSet<>(Topics.CONSUMED);
        names.addAll(Topics.ORDER_EXTRAS);
        OrderStatusPolicy.allStatuses().forEach(s -> names.add(Topics.orderStatus(s)));   // OrderService.updateStatus publishes these
        List<NewTopic> topics = new java.util.ArrayList<>();
        names.forEach(n -> topics.add(topic(n, replication)));
        Topics.CONSUMED.forEach(n -> topics.add(dlqTopic(n, replication)));
        return topics;
    }

    private static NewTopic topic(String name, short replication) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(replication).build();
    }

    private static NewTopic dlqTopic(String sourceTopic, short replication) {
        return TopicBuilder.name(Topics.dlq(sourceTopic)).partitions(1).replicas(replication).build();
    }
}
