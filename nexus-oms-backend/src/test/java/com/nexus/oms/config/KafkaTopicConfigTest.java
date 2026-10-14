package com.nexus.oms.config;

import com.nexus.oms.domain.OrderStatusPolicy;
import com.nexus.oms.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Every topic that is published or consumed exists, and every consumed one has a dead-letter topic. */
class KafkaTopicConfigTest {

    private final List<NewTopic> topics = new KafkaTopicConfig().omsTopics();
    private final Set<String> names = topics.stream().map(NewTopic::name).collect(Collectors.toSet());

    @Test
    void everyConsumedTopic_hasADeadLetterTopic() {
        for (String t : Topics.CONSUMED) {
            assertTrue(names.contains(t), t);
            assertTrue(names.contains(Topics.dlq(t)), "missing " + Topics.dlq(t) + ": the failure path would itself fail");
        }
    }

    @Test
    void everyOrderStatusTopic_theServicePublishes_isDeclared() {
        for (String status : OrderStatusPolicy.allStatuses()) {
            assertTrue(names.contains(Topics.orderStatus(status)), Topics.orderStatus(status));
        }
        Topics.ORDER_EXTRAS.forEach(t -> assertTrue(names.contains(t), t));
    }

    @Test
    void noTopicIsDeclaredTwice() {
        assertEquals(topics.size(), names.size());
    }

    @Test
    void theStatusSetCoversTheWholePolicy() {
        assertEquals(16, OrderStatusPolicy.allStatuses().size());
    }
}
