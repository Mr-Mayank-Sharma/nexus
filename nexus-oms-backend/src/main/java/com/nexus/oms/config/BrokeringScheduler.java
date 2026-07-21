package com.nexus.oms.config;

import com.nexus.oms.service.BrokeringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class BrokeringScheduler {

    private static final Logger log = LoggerFactory.getLogger(BrokeringScheduler.class);

    private final BrokeringService brokeringService;

    public BrokeringScheduler(BrokeringService brokeringService) {
        this.brokeringService = brokeringService;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void processBrokeringQueue() {
        try {
            brokeringService.processBrokeringQueue();
        } catch (Exception e) {
            log.error("Brokering scheduler error: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 */2 * * * ?")
    public void processPriorityQueue() {
        try {
            brokeringService.processPriorityQueue();
        } catch (Exception e) {
            log.error("Priority brokering scheduler error: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void expireStaleOrders() {
        try {
            brokeringService.expireStaleOrders();
        } catch (Exception e) {
            log.error("Expire stale orders error: {}", e.getMessage());
        }
    }
}
