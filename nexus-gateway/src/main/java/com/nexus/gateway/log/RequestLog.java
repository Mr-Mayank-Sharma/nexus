package com.nexus.gateway.log;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Every call with its client, store, provider, outcome and time taken: the gateway's health and audit trail. */
@Component
public class RequestLog {

    private static final int MAX_REFERENCE_LENGTH = 255;
    private static final int MAX_ERROR_LENGTH = 4000;

    private final JdbcTemplate jdbc;

    public RequestLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs the call and logs it, whether it worked or not; a failure is logged and thrown on. */
    public <T> T timed(String clientId, String storeId, String operation, String provider, String reference, Supplier<T> call) {
        long startedNanos = System.nanoTime();
        try {
            T result = call.get();
            write(clientId, storeId, operation, provider, "OK", startedNanos, reference, null);
            return result;
        } catch (RuntimeException failure) {
            write(clientId, storeId, operation, provider, "ERROR", startedNanos, reference, failure.getMessage());
            throw failure;
        }
    }

    private void write(String clientId, String storeId, String operation, String provider, String outcome, long startedNanos, String reference,
                       String error) {
        long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000;
        jdbc.update("INSERT INTO request_log (request_id, client_id, store_id, operation, provider, outcome, duration_ms, reference, error_text) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), clientId, storeId, operation, provider, outcome, durationMillis,
                cut(reference, MAX_REFERENCE_LENGTH), cut(error, MAX_ERROR_LENGTH));
    }

    /** The latest calls, of one client or (null) all. */
    public List<Map<String, Object>> recent(String clientId, int limit) {
        if (clientId == null) {
            return jdbc.queryForList("SELECT * FROM request_log ORDER BY created_at DESC LIMIT ?", limit);
        }
        return jdbc.queryForList("SELECT * FROM request_log WHERE client_id = ? ORDER BY created_at DESC LIMIT ?", clientId, limit);
    }

    /** Calls, failures and average time per operation and provider over the last hours. */
    public List<Map<String, Object>> health(int hours) {
        Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusHours(hours));
        return jdbc.queryForList("SELECT operation, provider, COUNT(*) AS calls, SUM(CASE WHEN outcome = 'ERROR' THEN 1 ELSE 0 END) AS errors, "
                + "AVG(duration_ms) AS avg_ms, MAX(duration_ms) AS max_ms FROM request_log WHERE created_at >= ? "
                + "GROUP BY operation, provider ORDER BY operation, provider", since);
    }

    private static String cut(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
