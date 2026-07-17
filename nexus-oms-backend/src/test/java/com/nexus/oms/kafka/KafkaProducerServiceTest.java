package com.nexus.oms.kafka;

import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaProducerService producerService;

    @BeforeEach
    void setUp() {
        producerService = new KafkaProducerService(kafkaTemplate);
    }

    @Test
    void publish_sendsToKafka() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("test.topic"), anyString())).thenReturn(future);

        producerService.publish("test.topic", "hello");

        verify(kafkaTemplate).send("test.topic", "hello");
    }

    @Test
    void publish_withKey_sendsToKafka() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("test.topic"), anyString(), anyString())).thenReturn(future);

        producerService.publish("test.topic", "my-key", "hello");

        verify(kafkaTemplate).send("test.topic", "my-key", "hello");
    }

    @Test
    void publish_throwsOnFailure() {
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenThrow(new RuntimeException("kafka down"));

        assertThrows(RuntimeException.class, () -> producerService.publish("test.topic", "msg"));
    }

    @Test
    void publishEvent_sendsWrappedEvent() {
        UUID tenantId = UUID.randomUUID();
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            producerService.publishEvent("order.created", "ORDER_CREATED", "{\"id\":\"123\"}");

            verify(kafkaTemplate).send(eq("order.created"), anyString(), argThat(json -> {
                EventRecord event = EventRecord.fromJson(json);
                return event.eventType().equals("ORDER_CREATED")
                        && event.data().equals("{\"id\":\"123\"}")
                        && event.tenantId().equals(tenantId);
            }));
        }
    }

    @Test
    void publishEvent_withTenantId_sendsWrappedEvent() {
        UUID tenantId = UUID.randomUUID();
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        producerService.publishEvent("order.created", "ORDER_CREATED", "order-123", tenantId);

        verify(kafkaTemplate).send(eq("order.created"), anyString(), argThat(json -> {
            EventRecord event = EventRecord.fromJson(json);
            return event.data().equals("order-123") && tenantId.equals(event.tenantId());
        }));
    }
}
