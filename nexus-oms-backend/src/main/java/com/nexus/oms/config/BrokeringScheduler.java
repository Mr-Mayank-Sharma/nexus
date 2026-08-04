package com.nexus.oms.config;

import com.nexus.oms.service.BrokeringService;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.UUID;

@Configuration
@EnableScheduling
public class BrokeringScheduler {

    private static final Logger log = LoggerFactory.getLogger(BrokeringScheduler.class);

    private final BrokeringService brokeringService;
    private final JdbcTemplate jdbcTemplate;

    public BrokeringScheduler(BrokeringService brokeringService, JdbcTemplate jdbcTemplate) {
        this.brokeringService = brokeringService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private List<UUID> getActiveTenantIds() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT tenant_id FROM nx_users WHERE tenant_id IS NOT NULL",
                UUID.class);
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void processBrokeringQueue() {
        for (UUID tenantId : getActiveTenantIds()) {
            try {
                TenantContext.setCurrentTenantId(tenantId);
                brokeringService.processBrokeringQueue();
            } catch (Exception e) {
                log.error("Brokering scheduler error for tenant {}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(cron = "0 */2 * * * ?")
    public void processPriorityQueue() {
        for (UUID tenantId : getActiveTenantIds()) {
            try {
                TenantContext.setCurrentTenantId(tenantId);
                brokeringService.processPriorityQueue();
            } catch (Exception e) {
                log.error("Priority brokering scheduler error for tenant {}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void expireStaleOrders() {
        for (UUID tenantId : getActiveTenantIds()) {
            try {
                TenantContext.setCurrentTenantId(tenantId);
                brokeringService.expireStaleOrders();
            } catch (Exception e) {
                log.error("Expire stale orders error for tenant {}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
