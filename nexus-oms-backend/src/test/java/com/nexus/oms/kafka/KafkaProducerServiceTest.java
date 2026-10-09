package com.nexus.oms.kafka;

import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Transactional outbox: events are written in the caller's transaction and delivered by the
 * relay afterwards. Runs against a real (H2) table, not mocks of JDBC.
 */
class KafkaProducerServiceTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private KafkaProducerService producer;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true)
                .addScript("schema.sql").build();
        jdbc = new JdbcTemplate(db);
        tx = new TransactionTemplate(new DataSourceTransactionManager(db));
        producer = new KafkaProducerService(new OutboxWriter(jdbc));
        relay = new OutboxRelay(jdbc, kafka);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        db.shutdown();
    }

    @Test
    void publish_writesToOutbox_andNeverTouchesKafkaDirectly() {
        producer.publish("order.created", "k1", "{\"a\":1}");

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT topic, msg_key, payload, status FROM nx_outbox");
        assertEquals(1, rows.size());
        assertEquals("order.created", rows.get(0).get("TOPIC"));
        assertEquals("k1", rows.get(0).get("MSG_KEY"));
        assertEquals("PENDING", rows.get(0).get("STATUS"));
        verifyNoInteractions(kafka);
    }

    @Test
    void eventFromARolledBackTransaction_isNeverSent() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            producer.publish("order.created", "k1", "{}");
            throw new IllegalStateException("business failure after the event was raised");
        }));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM nx_outbox", Integer.class));
        assertEquals(0, relay.relayBatch());
        verifyNoInteractions(kafka);
    }

    @Test
    void publishEvent_recordsTenant() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenant);

        producer.publishEvent("order.created", "ORDER_CREATED", "{\"id\":\"o1\"}");

        assertEquals(tenant, jdbc.queryForObject("SELECT tenant_id FROM nx_outbox", UUID.class));
        String payload = jdbc.queryForObject("SELECT payload FROM nx_outbox", String.class);
        assertTrue(payload.contains("ORDER_CREATED"));
    }

    @Test
    void relay_sendsInOrder_andMarksSent() {
        when(kafka.send(anyString(), any(), anyString())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        producer.publish("t", "a", "first");
        producer.publish("t", "b", "second");

        assertEquals(2, relay.relayBatch());

        var order = inOrder(kafka);
        order.verify(kafka).send("t", "a", "first");
        order.verify(kafka).send("t", "b", "second");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM nx_outbox WHERE status = 'SENT'", Integer.class));
        assertEquals(0, relay.relayBatch(), "sent rows are not sent again");
    }

    @Test
    void relay_pausesWhenKafkaIsDown_andResumesLater() {
        when(kafka.send(anyString(), any(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        producer.publish("t", "a", "first");
        producer.publish("t", "b", "second");

        assertEquals(0, relay.relayBatch());
        verify(kafka, times(1)).send(anyString(), any(), anyString());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM nx_outbox WHERE msg_key = 'a'", Integer.class));

        reset(kafka);
        when(kafka.send(anyString(), any(), anyString())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        assertEquals(2, relay.relayBatch());
    }

    @Test
    void relay_parksAPoisonEventAfterMaxAttempts() {
        when(kafka.send(eq("t"), eq("bad"), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("rejected")));
        when(kafka.send(eq("t"), eq("good"), anyString())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        producer.publish("t", "bad", "x");
        producer.publish("t", "good", "y");

        for (int i = 0; i < OutboxRelay.MAX_ATTEMPTS - 1; i++) relay.relayBatch();
        assertEquals(1, relay.relayBatch(), "after the last attempt the bad event is parked and the queue moves on");

        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM nx_outbox WHERE msg_key = 'bad'", String.class));
        assertEquals("SENT", jdbc.queryForObject("SELECT status FROM nx_outbox WHERE msg_key = 'good'", String.class));
    }
}
