package com.nexus.oms.jobs;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Starts the jobs that are due. The lock keeps two Nexus instances from starting the same run. */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);
    private final JobService service;

    public JobScheduler(JobService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    @SchedulerLock(name = "JobScheduler.tick", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void tick() {
        try {
            int closed = service.failStaleRuns();
            if (closed > 0) log.warn("Closed {} job run(s) that never reported back", closed);
            for (NxJob job : service.due()) {
                try {
                    service.runScheduled(job);
                } catch (Exception e) {
                    log.warn("Could not start job {}: {}", job.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Job scheduler tick failed: {}", e.getMessage(), e);
        }
    }
}
