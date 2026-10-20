package com.nexus.oms.config;

import com.nexus.oms.service.FulfilmentReleaseService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Every 10 minutes, releases orders whose ship-date hold has ended, so brokering can allocate them. */
@Component
public class FulfilmentReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentReleaseScheduler.class);

    private final FulfilmentReleaseService service;

    public FulfilmentReleaseScheduler(FulfilmentReleaseService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(name = "FulfilmentReleaseScheduler.releaseDue", lockAtMostFor = "PT9M", lockAtLeastFor = "PT30S")
    public void releaseDue() {
        try {
            int n = service.releaseDue(LocalDateTime.now());
            if (n > 0) log.info("Released {} order(s) whose ship-date hold ended", n);
        } catch (Exception e) {
            log.error("Release scheduler error: {}", e.getMessage());
        }
    }
}
