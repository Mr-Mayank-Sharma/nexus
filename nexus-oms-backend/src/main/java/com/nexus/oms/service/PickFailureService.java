package com.nexus.oms.service;

import com.nexus.oms.repository.OrderAllocationRepository;
import com.nexus.oms.repository.OrderRejectionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How often each store fails to pick what it was allocated, from Nexus's own records.
 *
 * <p>Peer-reviewed work on ship-from-store (Das, Ravi and Sridhar, 2023) found that ordering store attempts
 * by their chance of failing, and not only by cost, cut fulfilment cost by up to 22% on real retailer data,
 * and recommends keeping pick-failure probabilities inside the order management system. This is that number.
 *
 * <p>A store with 2 rejections in 3 allocated units is not "67% failing"; it is unknown. The rate is therefore
 * smoothed towards a prior ({@value #PRIOR_RATE}) with the weight of {@value #PRIOR_UNITS} units, so a new or
 * quiet store sits at the prior and a store with a long record moves to its own rate.
 */
@Service
public class PickFailureService {

    public static final int LOOKBACK_DAYS = 90;
    /** What a store with no history is assumed to fail at. A starting point, not a measured industry figure. */
    public static final double PRIOR_RATE = 0.05;
    /** How many allocated units the prior is worth. */
    public static final double PRIOR_UNITS = 20;

    public record NodeRate(UUID nodeId, long allocatedUnits, long rejectedUnits, double rate) {}

    private final OrderAllocationRepository allocations;
    private final OrderRejectionRepository rejections;

    public PickFailureService(OrderAllocationRepository allocations, OrderRejectionRepository rejections) {
        this.allocations = allocations;
        this.rejections = rejections;
    }

    /** Smoothed failure rate for every store that has been allocated or has rejected anything lately. */
    public Map<UUID, NodeRate> ratesFor(UUID tenantId) {
        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        Map<UUID, long[]> counts = new HashMap<>();   // [allocated, rejected]
        for (Object[] row : allocations.allocatedUnitsByNode(tenantId, since)) {
            counts.computeIfAbsent((UUID) row[0], k -> new long[2])[0] = ((Number) row[1]).longValue();
        }
        for (Object[] row : rejections.rejectedUnitsByNode(tenantId, since)) {
            counts.computeIfAbsent((UUID) row[0], k -> new long[2])[1] = ((Number) row[1]).longValue();
        }
        Map<UUID, NodeRate> result = new HashMap<>();
        counts.forEach((node, c) -> result.put(node, new NodeRate(node, c[0], c[1], smoothed(c[1], c[0]))));
        return result;
    }

    /** The rate to use for a store: its own smoothed rate, or the prior when it has no history. */
    public static double rateOrPrior(Map<UUID, NodeRate> rates, UUID nodeId) {
        NodeRate r = rates == null ? null : rates.get(nodeId);
        return r == null ? PRIOR_RATE : r.rate();
    }

    static double smoothed(long rejected, long allocated) {
        double r = Math.max(0, rejected);
        double a = Math.max(0, allocated);
        return Math.min(1.0, (r + PRIOR_RATE * PRIOR_UNITS) / (a + PRIOR_UNITS));
    }
}
