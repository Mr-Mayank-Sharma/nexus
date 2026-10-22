package com.nexus.oms.coremigration;

import com.nexus.core.job.CoreJobHandler;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.SystemPropertyService;
import com.nexus.core.tenancy.TenantScope;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the core order model in step with the earlier model while both run side by side: every minute, for each
 * company whose orders changed since the last run, it copies new customers, products, locations, stock items and
 * orders, then brings status and tracking changes across to orders already copied. The earlier model stays the
 * system of record and every screen still reads it; a failure here is logged and never touches an import.
 * It runs as the {@code core_mirror} job (pause it there). The last run time is kept as a system property, so a
 * restart picks up what changed while the server was down.
 */
@Component
public class CoreMirror implements CoreJobHandler {

    private static final Logger log = LoggerFactory.getLogger(CoreMirror.class);
    static final String RESOURCE = "nexus.core";
    static final String LAST_RUN = "mirror.lastRun";
    /** Before the first pass, everything counts as changed. */
    static final LocalDateTime BEGINNING = LocalDateTime.of(2000, 1, 1, 0, 0);
    /** A row committed just before the last pass started may carry an older updated_at, so passes overlap by a minute. */
    static final long OVERLAP_MINUTES = 1;

    private final JdbcTemplate jdbc;
    private final CustomerPartyBackfill customerCopy;
    private final ProductBackfill productCopy;
    private final FacilityInventoryBackfill stockCopy;
    private final OrderBackfill orderCopy;
    private final SystemPropertyService systemPropertyService;
    private final TenantScope tenantScope;

    public CoreMirror(JdbcTemplate jdbc, CustomerPartyBackfill customerCopy, ProductBackfill productCopy, FacilityInventoryBackfill stockCopy,
                      OrderBackfill orderCopy, SystemPropertyService systemPropertyService, TenantScope tenantScope) {
        this.jdbc = jdbc;
        this.customerCopy = customerCopy;
        this.productCopy = productCopy;
        this.stockCopy = stockCopy;
        this.orderCopy = orderCopy;
        this.systemPropertyService = systemPropertyService;
        this.tenantScope = tenantScope;
    }

    public record Run(int companies, int ordersCopied, int ordersRefreshed, int failures) {
    }

    /** The core_mirror job. */
    @Override
    public String run(Map<String, String> parameters) {
        // The job is seeded in every core database, but one pass already covers every company: run it from the shared one.
        if (!TenantScope.SHARED.equals(tenantScope.currentDatabaseKey())) {
            return "runs from the shared database only";
        }
        Run pass = runOnce();
        return pass.companies() + " companies, " + pass.ordersCopied() + " orders copied, " + pass.ordersRefreshed() + " refreshed, "
                + pass.failures() + " failures";
    }

    /**
     * One pass over every company whose orders changed since the last pass. The last-run time only moves on when every
     * company copied cleanly, so a failed company is tried again next time.
     */
    public Run runOnce() {
        LocalDateTime startedAt = CoreClock.now();
        LocalDateTime lastPass = systemPropertyService.get(RESOURCE, LAST_RUN).map(LocalDateTime::parse).orElse(BEGINNING);
        LocalDateTime changedSince = lastPass.minusMinutes(OVERLAP_MINUTES);
        List<UUID> changedCompanies = jdbc.queryForList("SELECT DISTINCT tenant_id FROM nx_orders WHERE updated_at >= ? AND tenant_id IS NOT NULL",
                UUID.class, Timestamp.valueOf(changedSince));

        int ordersCopied = 0;
        int ordersRefreshed = 0;
        int failures = 0;
        for (UUID tenantId : changedCompanies) {
            try {
                TenantContext.setCurrentTenantId(tenantId);
                customerCopy.run(tenantId);
                try {
                    productCopy.run(tenantId);
                } catch (IllegalStateException noCompanyCurrency) {
                    // No company currency yet: products wait, orders still copy (their items keep the description).
                    log.warn("Core mirror: products of company {} not copied: {}", tenantId, noCompanyCurrency.getMessage());
                }
                stockCopy.run(tenantId);
                ordersCopied += orderCopy.run(tenantId).ordersCreated();
                ordersRefreshed += orderCopy.refresh(tenantId, changedSince);
            } catch (Exception failure) {
                failures++;
                log.error("Core mirror failed for company {}: {}", tenantId, failure.getMessage(), failure);
            } finally {
                TenantContext.clear();
            }
        }
        if (failures == 0) {
            systemPropertyService.set(RESOURCE, LAST_RUN, startedAt.toString(), "When the core mirror last finished a pass without failures");
        }
        return new Run(changedCompanies.size(), ordersCopied, ordersRefreshed, failures);
    }
}
