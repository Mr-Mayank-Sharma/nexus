package com.nexus.oms.coreapi;

import com.nexus.core.job.CoreJobHandler;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The core_routing job: runs each routing group that has a schedule ({@code schedule_minutes}) when its last real run is
 * that old. The job is seeded paused: while the core mirrors the earlier model, routing there holds stock in the core
 * only, and turning it on is a decision for the people running the shop. One group failing does not stop the others.
 */
@Component("coreRoutingJob")
public class CoreRoutingJob implements CoreJobHandler {

    private static final Logger log = LoggerFactory.getLogger(CoreRoutingJob.class);
    static final String JOB_USER = "core_routing";

    private final RoutingService routingService;

    public CoreRoutingJob(RoutingService routingService) {
        this.routingService = routingService;
    }

    @Override
    public String run(Map<String, String> parameters) {
        List<String> outcomes = new ArrayList<>();
        for (String routingGroupId : routingService.scheduledGroupsDue(CoreClock.now())) {
            try {
                RoutingService.RunResult result = routingService.run(routingGroupId, JOB_USER);
                outcomes.add(routingGroupId + ": " + result.routed() + " routed of " + result.considered());
            } catch (Exception failure) {
                log.error("Routing group {} failed: {}", routingGroupId, failure.getMessage(), failure);
                outcomes.add(routingGroupId + ": failed: " + failure.getMessage());
            }
        }
        return outcomes.isEmpty() ? "no group due" : String.join("; ", outcomes);
    }
}
