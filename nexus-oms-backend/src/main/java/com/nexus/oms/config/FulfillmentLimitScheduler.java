package com.nexus.oms.config;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.nexus.oms.service.FulfillmentLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class FulfillmentLimitScheduler {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentLimitScheduler.class);

    private final FulfillmentLimitService fulfillmentLimitService;

    public FulfillmentLimitScheduler(FulfillmentLimitService fulfillmentLimitService) {
        this.fulfillmentLimitService = fulfillmentLimitService;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @SchedulerLock(name = "FulfillmentLimitScheduler.resetDailyCounts", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void resetDailyCounts() {
        try {
            fulfillmentLimitService.resetDailyCounts();
        } catch (Exception e) {
            log.error("Reset daily counts error: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 0 * * MON")
    @SchedulerLock(name = "FulfillmentLimitScheduler.resetWeeklyCounts", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void resetWeeklyCounts() {
        try {
            fulfillmentLimitService.resetWeeklyCounts();
        } catch (Exception e) {
            log.error("Reset weekly counts error: {}", e.getMessage());
        }
    }
}
