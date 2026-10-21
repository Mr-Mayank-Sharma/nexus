package com.nexus.oms.coreapi;

import com.nexus.core.job.CoreJobHandler;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The core_routing job: runs each routing group that has a schedule ({@code schedule_minutes}) when its last real run is
 * that old. The job is seeded paused: while the core mirrors the earlier model, routing there holds stock in the core
 * only, and turning it on is a decision for the people running the shop.
 */
@Component("coreRoutingJob")
public class CoreRoutingJob implements CoreJobHandler {

    private static final Logger log = LoggerFactory.getLogger(CoreRoutingJob.class);

    private final JdbcTemplate jdbc;
    private final RoutingService routing;

    public CoreRoutingJob(JdbcTemplate jdbc, RoutingService routing) {
        this.jdbc = jdbc;
        this.routing = routing;
    }

    @Override
    public String run(Map<String, String> parameters) {
        LocalDateTime now = CoreClock.now();
        List<Map<String, Object>> groups = jdbc.queryForList("SELECT g.routing_group_id, g.schedule_minutes, MAX(r.start_date) AS last_run "
                + "FROM order_routing_group g LEFT JOIN order_routing_run r ON r.routing_group_id = g.routing_group_id AND r.is_simulation = 'N' "
                + "WHERE g.schedule_minutes IS NOT NULL AND g.schedule_minutes > 0 GROUP BY g.routing_group_id, g.schedule_minutes");
        List<String> out = new ArrayList<>();
        for (Map<String, Object> g : groups) {
            Timestamp last = (Timestamp) g.get("last_run");
            long every = ((Number) g.get("schedule_minutes")).longValue();
            if (last != null && last.toLocalDateTime().plusMinutes(every).isAfter(now)) continue;
            String id = String.valueOf(g.get("routing_group_id"));
            try {
                RoutingService.RunResult r = routing.run(id, "core_routing");
                out.add(id + ": " + r.routed() + " routed of " + r.considered());
            } catch (Exception e) {
                log.error("Routing group {} failed: {}", id, e.getMessage(), e);
                out.add(id + ": failed: " + e.getMessage());
            }
        }
        return out.isEmpty() ? "no group due" : String.join("; ", out);
    }
}
