package com.nexus.oms.kafka;

import com.nexus.oms.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Appends an event to {@code nx_outbox}. JdbcTemplate joins the caller's Spring transaction,
 * so the event commits or rolls back together with the business change that produced it.
 */
@Component
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;

    public OutboxWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID enqueue(String topic, String key, String payload) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO nx_outbox (id, tenant_id, topic, msg_key, payload, status, attempts, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?)",
                id, currentTenantOrNull(), topic, key, payload, Timestamp.valueOf(LocalDateTime.now()));
        return id;
    }

    private static UUID currentTenantOrNull() {
        try {
            return TenantContext.getCurrentTenantId();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
