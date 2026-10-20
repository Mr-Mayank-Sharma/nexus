package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxRoutingConfig;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.BrokeringQueueRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.RoutingConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * When fulfilment of an order may start. An order with a requested ship date is held until its release time,
 * ship date minus a window per order type (B2B: 3 days, B2C: 1 day by default, set per tenant). While held it is not
 * allocated, so it reserves no stock: a wholesale order placed two months ahead does not lock away units that orders
 * shipping this week need. At the release time (or when someone releases it by hand) it goes to brokering.
 * Orders without a ship date are never held.
 */
@Service
public class FulfilmentReleaseService {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentReleaseService.class);

    public static final int DEFAULT_DAYS_B2B = 3;
    public static final int DEFAULT_DAYS_B2C = 1;
    public static final String AWAITING_RELEASE = "AWAITING_RELEASE";
    static final List<String> OPEN = List.of("PENDING", "CONFIRMED");

    private final OrderRepository orders;
    private final RoutingConfigRepository configs;
    private final BrokeringQueueRepository queue;
    private final BrokeringService brokering;

    public FulfilmentReleaseService(OrderRepository orders, RoutingConfigRepository configs, BrokeringQueueRepository queue,
                                    @Lazy BrokeringService brokering) {
        this.orders = orders;
        this.configs = configs;
        this.queue = queue;
        this.brokering = brokering;
    }

    public record Policy(int b2bDays, int b2cDays) {}

    public Policy policy(UUID tenantId) {
        return configs.findByTenantId(tenantId)
                .map(c -> new Policy(nz(c.getReleaseDaysB2b(), DEFAULT_DAYS_B2B), nz(c.getReleaseDaysB2c(), DEFAULT_DAYS_B2C)))
                .orElse(new Policy(DEFAULT_DAYS_B2B, DEFAULT_DAYS_B2C));
    }

    /** Changes the windows and re-plans every order still on hold. */
    public Policy updatePolicy(UUID tenantId, int b2bDays, int b2cDays) {
        if (b2bDays < 0 || b2bDays > 180 || b2cDays < 0 || b2cDays > 180) throw new BadRequestException("Release windows must be 0 to 180 days");
        NxRoutingConfig c = configs.findByTenantId(tenantId).orElseThrow(() -> new BadRequestException("Set up routing for this workspace first"));
        c.setReleaseDaysB2b(b2bDays);
        c.setReleaseDaysB2c(b2cDays);
        configs.save(c);
        Policy p = new Policy(b2bDays, b2cDays);
        for (NxOrder o : orders.findByTenantIdAndReleasedAtIsNullAndReleaseAtIsNotNullOrderByReleaseAtAsc(tenantId)) {
            if (!OPEN.contains(o.getStatus())) continue;
            plan(o, p);
            orders.save(o);
        }
        return p;
    }

    /** Sets the release time from the order's type and requested ship date. Does not save. */
    public void plan(NxOrder o, Policy p) {
        String type = normaliseType(o.getOrderType());
        o.setOrderType(type);
        if (o.getRequestedShipDate() == null || o.getReleasedAt() != null) {
            o.setReleaseAt(null);
            if (AWAITING_RELEASE.equals(o.getSubStatus())) o.setSubStatus(null);
            return;
        }
        o.setReleaseAt(o.getRequestedShipDate().minusDays("B2B".equals(type) ? p.b2bDays() : p.b2cDays()));
        if (isHeld(o, LocalDateTime.now())) o.setSubStatus(AWAITING_RELEASE);
        else if (AWAITING_RELEASE.equals(o.getSubStatus())) o.setSubStatus(null);
    }

    public void plan(NxOrder o) {
        plan(o, policy(o.getTenantId()));
    }

    /** Held = has a release time in the future and has not been released. */
    public static boolean isHeld(NxOrder o, LocalDateTime now) {
        return o.getReleasedAt() == null && o.getReleaseAt() != null && o.getReleaseAt().isAfter(now);
    }

    /** Sets the type and requested ship date of an open order, and re-plans its release. */
    public NxOrder schedule(UUID tenantId, UUID orderId, String orderType, LocalDateTime requestedShipDate) {
        NxOrder o = own(tenantId, orderId);
        if (!OPEN.contains(o.getStatus())) {
            throw new BadRequestException("Only orders not yet allocated can be rescheduled (this one is " + o.getStatus() + ")");
        }
        boolean wasHeld = isHeld(o, LocalDateTime.now());
        if (orderType != null) o.setOrderType(normaliseType(orderType));
        o.setRequestedShipDate(requestedShipDate);
        o.setReleasedAt(null);
        plan(o);
        // moved inside its window (or its date cleared): it may start now, so hand it to brokering
        if (wasHeld && !isHeld(o, LocalDateTime.now())) return release(o, "after rescheduling");
        return orders.save(o);
    }

    /** Ends the hold now and hands the order to brokering. */
    public NxOrder releaseNow(UUID tenantId, UUID orderId) {
        NxOrder o = own(tenantId, orderId);
        if (!OPEN.contains(o.getStatus())) throw new BadRequestException("Order is " + o.getStatus() + "; only open orders are released");
        return release(o, "by hand");
    }

    /** Orders waiting for release in a tenant, soonest first. */
    public List<NxOrder> held(UUID tenantId) {
        LocalDateTime now = LocalDateTime.now();
        return orders.findByTenantIdAndReleasedAtIsNullAndReleaseAtIsNotNullOrderByReleaseAtAsc(tenantId).stream()
                .filter(o -> OPEN.contains(o.getStatus()) && isHeld(o, now)).toList();
    }

    /** Releases every order whose time has come, across tenants. Called by the scheduler. */
    public int releaseDue(LocalDateTime now) {
        int n = 0;
        for (NxOrder o : orders.findTop500ByReleasedAtIsNullAndReleaseAtLessThanEqualAndStatusInOrderByReleaseAtAsc(now, OPEN)) {
            try {
                com.nexus.oms.security.TenantContext.setCurrentTenantId(o.getTenantId());
                release(o, "on schedule");
                n++;
            } catch (Exception e) {
                log.warn("Could not release order {}: {}", o.getId(), e.getMessage());
            } finally {
                com.nexus.oms.security.TenantContext.clear();
            }
        }
        return n;
    }

    private NxOrder release(NxOrder o, String how) {
        o.setReleasedAt(LocalDateTime.now());
        if (AWAITING_RELEASE.equals(o.getSubStatus())) o.setSubStatus(null);
        NxOrder saved = orders.save(o);
        // a queue entry that was parked until the release time runs now; otherwise the order joins the queue
        var waiting = queue.findByOrderId(o.getId()).stream().filter(q -> "WAITING".equals(q.getStatus())).findFirst();
        if (waiting.isPresent()) {
            waiting.get().setNextRunAt(LocalDateTime.now());
            queue.save(waiting.get());
        } else {
            try { brokering.enqueueOrder(o.getId(), "HIGH"); } catch (Exception e) { log.warn("Released order {} could not be queued: {}", o.getId(), e.getMessage()); }
        }
        log.info("Order {} released {} (ship date {})", o.getId(), how, o.getRequestedShipDate());
        return saved;
    }

    private NxOrder own(UUID tenantId, UUID orderId) {
        NxOrder o = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!tenantId.equals(o.getTenantId())) throw new ResourceNotFoundException("Order", orderId);
        return o;
    }

    public static String normaliseType(String t) {
        if (t == null || t.isBlank()) return "B2C";
        String u = t.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("B2C", "B2B").contains(u)) throw new BadRequestException("orderType must be B2C or B2B");
        return u;
    }

    private static int nz(Integer v, int d) { return v == null ? d : v; }
}
