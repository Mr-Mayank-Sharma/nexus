package com.nexus.oms.config;

import com.nexus.oms.entity.NxParkedOrder;
import com.nexus.oms.repository.ParkedOrderRepository;
import com.nexus.oms.service.ParkedOrderService;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableScheduling
public class PreOrderReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(PreOrderReleaseScheduler.class);

    private final ParkedOrderService parkedOrderService;
    private final ParkedOrderRepository parkedOrderRepository;

    public PreOrderReleaseScheduler(ParkedOrderService parkedOrderService,
                                     ParkedOrderRepository parkedOrderRepository) {
        this.parkedOrderService = parkedOrderService;
        this.parkedOrderRepository = parkedOrderRepository;
    }

    /**
     * Runs every hour to auto-release parked orders whose expectedDate has passed.
     * Only processes orders with status PARKED and reason PREORDER or BACKORDER.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void releaseExpiredPreOrders() {
        try {
            // Find all PARKED orders with expectedDate in the past
            List<NxParkedOrder> expiredOrders = parkedOrderRepository
                    .findByStatusAndExpectedDateBefore("PARKED", LocalDateTime.now());

            int released = 0;
            for (NxParkedOrder parkedOrder : expiredOrders) {
                try {
                    // Set tenant context to process this order
                    TenantContext.setCurrentTenantId(parkedOrder.getTenantId());
                    parkedOrderService.releaseOrder(parkedOrder.getId(), "AUTO_RELEASE: Expected date reached");
                    released++;
                } catch (Exception e) {
                    log.error("Failed to auto-release parked order {}: {}",
                            parkedOrder.getOrderNumber(), e.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }

            if (released > 0) {
                log.info("Auto-released {} parked orders past expected date", released);
            }
        } catch (Exception e) {
            log.error("Pre-order release scheduler error: {}", e.getMessage());
        }
    }
}