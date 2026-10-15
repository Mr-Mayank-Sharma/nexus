package com.nexus.oms.config;

import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.EquipmentWatchdog;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
public class EquipmentWatchdogScheduler {

    private static final Logger log = LoggerFactory.getLogger(EquipmentWatchdogScheduler.class);

    private final EquipmentWatchdog watchdog;
    private final JdbcTemplate jdbcTemplate;

    public EquipmentWatchdogScheduler(EquipmentWatchdog watchdog, JdbcTemplate jdbcTemplate) {
        this.watchdog = watchdog;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 * * * * ?")
    @SchedulerLock(name = "EquipmentWatchdogScheduler.sweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void sweep() {
        for (UUID tenantId : jdbcTemplate.queryForList(
                "SELECT DISTINCT tenant_id FROM nx_users WHERE tenant_id IS NOT NULL", UUID.class)) {
            try {
                TenantContext.setCurrentTenantId(tenantId);
                int n = watchdog.sweep(LocalDateTime.now());
                if (n > 0) log.info("Equipment watchdog acted on {} item(s) for tenant {}", n, tenantId);
            } catch (Exception e) {
                log.error("Equipment watchdog error for tenant {}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
