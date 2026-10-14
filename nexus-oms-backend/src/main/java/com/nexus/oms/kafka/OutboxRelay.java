package com.nexus.oms.kafka;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes committed outbox rows to Kafka, oldest first. Delivery is at-least-once: a crash
 * between send and the SENT update re-sends the row, so consumers must deduplicate by event
 * id (EventRecord carries one). A failing row stops the batch to preserve per-key order;
 * after {@link #MAX_ATTEMPTS} it is parked as FAILED so it cannot block the queue forever.
 */
@Component
@ConditionalOnProperty(name = "nexus.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    // Worst case for one run is BATCH_SIZE x SEND_TIMEOUT_MS = 300 s, comfortably inside the 10-minute
    // lock below, so a second instance can never take the lock while a batch is still being sent.
    static final int BATCH_SIZE = 100;
    static final int MAX_ATTEMPTS = 10;
    static final long SEND_TIMEOUT_MS = 3_000;
    static final int FAILED_RETENTION_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate,
                       io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        // Events that exhausted their retries sit as FAILED and were invisible; alert on this being > 0.
        meterRegistry.gauge("nexus.outbox.failed", this, r -> r.count("FAILED"));
        meterRegistry.gauge("nexus.outbox.pending", this, r -> r.count("PENDING"));
    }

    double count(String status) {
        try {
            Long n = jdbcTemplate.queryForObject("SELECT count(*) FROM nx_outbox WHERE status = ?", Long.class, status);
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            return Double.NaN;   // a gauge must never throw; NaN reads as "unknown"
        }
    }

    @Scheduled(fixedDelayString = "${nexus.outbox.relay-interval-ms:1000}")
    @SchedulerLock(name = "OutboxRelay.relay", lockAtMostFor = "PT10M", lockAtLeastFor = "PT0S")
    public void relay() {
        relayBatch();
    }

    /** Returns the number of rows sent. */
    int relayBatch() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, topic, msg_key, payload, attempts FROM nx_outbox "
                        + "WHERE status = 'PENDING' ORDER BY created_at, id LIMIT ?", BATCH_SIZE);
        int sent = 0;
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String topic = (String) row.get("topic");
            try {
                kafkaTemplate.send(topic, (String) row.get("msg_key"), (String) row.get("payload"))
                        .get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                jdbcTemplate.update("UPDATE nx_outbox SET status = 'SENT', sent_at = ?, last_error = NULL WHERE id = ?",
                        Timestamp.valueOf(LocalDateTime.now()), id);
                sent++;
            } catch (Exception e) {
                int attempts = ((Number) row.get("attempts")).intValue() + 1;
                String status = attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING";
                jdbcTemplate.update("UPDATE nx_outbox SET attempts = ?, status = ?, last_error = ? WHERE id = ?",
                        attempts, status, truncate(e.toString()), id);
                if ("FAILED".equals(status)) {
                    log.error("Outbox event {} to {} parked after {} attempts: {}", id, topic, attempts, e.getMessage());
                    continue;
                }
                log.warn("Outbox relay paused at event {} to {} (attempt {}): {}", id, topic, attempts, e.getMessage());
                break;
            }
        }
        return sent;
    }

    @Scheduled(cron = "0 15 2 * * *")
    @SchedulerLock(name = "OutboxRelay.purgeSent", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void purgeSent() {
        int removed = jdbcTemplate.update("DELETE FROM nx_outbox WHERE status = 'SENT' AND sent_at < ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(7)));
        // Parked events are kept for a month so they can be inspected and replayed, then dropped.
        int failed = jdbcTemplate.update("DELETE FROM nx_outbox WHERE status = 'FAILED' AND created_at < ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(FAILED_RETENTION_DAYS)));
        log.info("Outbox purge removed {} delivered events older than 7 days and {} failed events older than {} days",
                removed, failed, FAILED_RETENTION_DAYS);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}
