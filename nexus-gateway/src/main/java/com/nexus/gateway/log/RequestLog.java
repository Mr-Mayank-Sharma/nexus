package com.nexus.gateway.log;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Every call with its client, store, provider, outcome and time taken: the gateway's health and audit trail. */
@Component
public class RequestLog {

    private final JdbcTemplate jdbc;

    public RequestLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs the call and logs it, whether it worked or not. */
    public <T> T timed(String clientId, String storeId, String operation, String provider, String reference, Supplier<T> call) {
        long start = System.nanoTime();
        try {
            T result = call.get();
            write(clientId, storeId, operation, provider, "OK", start, reference, null);
            return result;
        } catch (RuntimeException e) {
            write(clientId, storeId, operation, provider, "ERROR", start, reference, e.getMessage());
            throw e;
        }
    }

    private void write(String clientId, String storeId, String operation, String provider, String outcome, long start, String reference, String error) {
        long ms = (System.nanoTime() - start) / 1_000_000;
        jdbc.update("INSERT INTO request_log (request_id, client_id, store_id, operation, provider, outcome, duration_ms, reference, error_text) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), clientId, storeId, operation, provider, outcome, ms,
                reference == null ? null : reference.length() > 255 ? reference.substring(0, 255) : reference,
                error == null ? null : error.length() > 4000 ? error.substring(0, 4000) : error);
    }

    public List<Map<String, Object>> recent(String clientId, int limit) {
        return clientId == null
                ? jdbc.queryForList("SELECT * FROM request_log ORDER BY created_at DESC LIMIT ?", limit)
                : jdbc.queryForList("SELECT * FROM request_log WHERE client_id = ? ORDER BY created_at DESC LIMIT ?", clientId, limit);
    }

    /** Calls, failures and average time per operation and provider over the last hours. */
    public List<Map<String, Object>> health(int hours) {
        return jdbc.queryForList("SELECT operation, provider, COUNT(*) AS calls, SUM(CASE WHEN outcome = 'ERROR' THEN 1 ELSE 0 END) AS errors, "
                + "AVG(duration_ms) AS avg_ms, MAX(duration_ms) AS max_ms FROM request_log WHERE created_at >= ? GROUP BY operation, provider ORDER BY operation, provider",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(hours)));
    }
}
