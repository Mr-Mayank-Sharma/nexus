package com.nexus.oms.coreapi;

import com.nexus.core.service.CoreClock;
import com.nexus.core.service.RoutingService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Runs each routing group that has a schedule ({@code schedule_minutes}) when its last run is that old. Off unless
 * {@code nexus.core.routing.enabled=true}: while the core mirrors the earlier model, routing there holds stock in the
 * core only, and turning it on is a decision for the people running the shop.
 */
@Component
public class CoreRoutingScheduler {

    private static final Logger log = LoggerFactory.getLogger(CoreRoutingScheduler.class);

    private final JdbcTemplate jdbc;
    private final RoutingService routing;
    private final boolean enabled;

    public CoreRoutingScheduler(JdbcTemplate jdbc, RoutingService routing, @Value("${nexus.core.routing.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.routing = routing;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${nexus.core.routing.tick-ms:60000}", initialDelayString = "${nexus.core.routing.tick-ms:60000}")
    @SchedulerLock(name = "CoreRoutingScheduler.tick", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void tick() {
        if (!enabled) return;
        LocalDateTime now = CoreClock.now();
        List<Map<String, Object>> due = jdbc.queryForList("SELECT g.routing_group_id, g.schedule_minutes, MAX(r.start_date) AS last_run "
                + "FROM order_routing_group g LEFT JOIN order_routing_run r ON r.routing_group_id = g.routing_group_id AND r.is_simulation = 'N' "
                + "WHERE g.schedule_minutes IS NOT NULL AND g.schedule_minutes > 0 GROUP BY g.routing_group_id, g.schedule_minutes");
        for (Map<String, Object> g : due) {
            Timestamp last = (Timestamp) g.get("last_run");
            long every = ((Number) g.get("schedule_minutes")).longValue();
            if (last != null && last.toLocalDateTime().plusMinutes(every).isAfter(now)) continue;
            String id = String.valueOf(g.get("routing_group_id"));
            try {
                RoutingService.RunResult r = routing.run(id, "scheduler");
                if (r.considered() > 0) log.info("Routing group {}: {} considered, {} routed, {} split, {} unfillable", id, r.considered(), r.routed(), r.split(), r.unfillable());
            } catch (Exception e) {
                log.error("Routing group {} failed: {}", id, e.getMessage(), e);
            }
        }
    }
}
