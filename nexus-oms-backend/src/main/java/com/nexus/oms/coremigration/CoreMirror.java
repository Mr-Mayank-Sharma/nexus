package com.nexus.oms.coremigration;

import com.nexus.core.service.CoreClock;
import com.nexus.core.service.SystemPropertyService;
import com.nexus.oms.security.TenantContext;
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
import java.util.UUID;

/**
 * Keeps the core order model in step with the earlier model while both run side by side: every minute, for each
 * company whose orders changed since the last run, it copies new customers, products, locations, stock items and
 * orders, then brings status and tracking changes across to orders already copied. The earlier model stays the
 * system of record and every screen still reads it; a failure here is logged and never touches an import.
 * Off with {@code nexus.core.mirror.enabled=false}. The last run time is kept as a system property, so a restart
 * picks up what changed while the server was down.
 */
@Component
public class CoreMirror {

    private static final Logger log = LoggerFactory.getLogger(CoreMirror.class);
    static final String RESOURCE = "nexus.core";
    static final String LAST_RUN = "mirror.lastRun";

    private final JdbcTemplate jdbc;
    private final CustomerPartyBackfill customers;
    private final ProductBackfill products;
    private final FacilityInventoryBackfill stock;
    private final OrderBackfill orders;
    private final SystemPropertyService properties;
    private final boolean enabled;

    public CoreMirror(JdbcTemplate jdbc, CustomerPartyBackfill customers, ProductBackfill products, FacilityInventoryBackfill stock,
                      OrderBackfill orders, SystemPropertyService properties,
                      @Value("${nexus.core.mirror.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.products = products;
        this.stock = stock;
        this.orders = orders;
        this.properties = properties;
        this.enabled = enabled;
    }

    public record Run(int companies, int ordersCopied, int ordersRefreshed, int failures) {
    }

    @Scheduled(fixedDelayString = "${nexus.core.mirror.interval-ms:60000}", initialDelayString = "${nexus.core.mirror.initial-delay-ms:60000}")
    @SchedulerLock(name = "CoreMirror.run", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void scheduled() {
        if (!enabled) return;
        try {
            Run r = runOnce();
            if (r.ordersCopied() > 0 || r.ordersRefreshed() > 0 || r.failures() > 0) {
                log.info("Core mirror: {} companies, {} orders copied, {} refreshed, {} failures", r.companies(), r.ordersCopied(),
                        r.ordersRefreshed(), r.failures());
            }
        } catch (Exception e) {
            log.error("Core mirror run failed: {}", e.getMessage(), e);
        }
    }

    /** One pass over every company whose orders changed since the last pass. */
    public Run runOnce() {
        LocalDateTime started = CoreClock.now();
        LocalDateTime since = properties.get(RESOURCE, LAST_RUN).map(LocalDateTime::parse).orElse(LocalDateTime.of(2000, 1, 1, 0, 0));
        // a minute of overlap: a row committed just before the last pass started may carry an older updated_at
        List<UUID> tenants = jdbc.queryForList("SELECT DISTINCT tenant_id FROM nx_orders WHERE updated_at >= ? AND tenant_id IS NOT NULL",
                UUID.class, Timestamp.valueOf(since.minusMinutes(1)));
        int copied = 0, refreshed = 0, failures = 0;
        for (UUID tenant : tenants) {
            try {
                TenantContext.setCurrentTenantId(tenant);
                customers.run(tenant);
                try {
                    products.run(tenant);
                } catch (IllegalStateException e) {
                    // no company currency yet: products wait, orders still copy (their items keep the description)
                    log.warn("Core mirror: products of company {} not copied: {}", tenant, e.getMessage());
                }
                stock.run(tenant);
                copied += orders.run(tenant).ordersCreated();
                refreshed += orders.refresh(tenant, since.minusMinutes(1));
            } catch (Exception e) {
                failures++;
                log.error("Core mirror failed for company {}: {}", tenant, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
        if (failures == 0) properties.set(RESOURCE, LAST_RUN, started.toString(), "When the core mirror last finished a pass without failures");
        return new Run(tenants.size(), copied, refreshed, failures);
    }
}
