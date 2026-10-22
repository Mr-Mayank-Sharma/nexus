package com.nexus.core.service;

import com.nexus.core.model.facility.Facility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Store reliability and phantom stock (doc 25, features 7 and 8). Both are transparent scores built from the history
 * the core already keeps, so a manager can see why: they are the rule-based fallback the doc plans for, and the place a
 * trained model's score will plug in.
 * <ul>
 *   <li>Reliability: the share of orders routed to a location that it picked, smoothed towards 90% so a location with
 *       few orders is not judged on two of them. Routing can sort by it.</li>
 *   <li>Phantom stock: per product and location, how likely the stock shown is not really there. It never changes
 *       stock; it ranks where a targeted count pays. See {@link #scorePhantomRisk} for the weights.</li>
 * </ul>
 */
@Service("coreStoreHealthService")
@CoreTransactional(readOnly = true)
public class StoreHealthService {

    /** The reliability prior: 90% worth 10 orders. */
    static final double PRIOR_SUCCESS = 9;
    static final double PRIOR_FAIL = 1;
    /** How a short pick shows in stock history: a release whose description starts with this. */
    static final String SHORT_PICK_PREFIX = "Short pick%";

    // Phantom stock weights. The total is clamped to 0..1.
    static final double PER_MISS = 0.35;
    static final double MAX_FOR_MISSES = 0.6;
    static final double PER_SHORT_COUNT = 0.15;
    static final double MAX_FOR_SHORT_COUNTS = 0.3;
    static final double NEVER_OR_LONG_AGO_COUNTED = 0.1;
    static final double RECENT_CLEAN_COUNT = -0.15;
    static final double SELLS_ELSEWHERE_NOT_HERE = 0.15;
    static final double VERY_LOW_QUANTITY = 0.05;

    // Phantom stock windows, in days.
    static final int SHORT_COUNT_DAYS = 180;
    static final int STALE_COUNT_DAYS = 90;
    static final int RECENT_COUNT_DAYS = 14;
    static final int SELLING_ELSEWHERE_DAYS = 30;
    static final int NOT_SHIPPED_HERE_DAYS = 60;
    static final BigDecimal VERY_LOW_SHOWN = BigDecimal.valueOf(2);

    private final EntityManager entityManager;

    public StoreHealthService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ── reliability ──

    public record Reliability(String facilityId, String facilityName, long ordersRouted, long pickFailures, double score, String reason) {
    }

    /** Reliability of every location that had orders or failures in the window, least reliable first. */
    public List<Reliability> reliability(int days) {
        LocalDateTime since = CoreClock.now().minusDays(days);
        Map<String, Long> routedByFacility = countByFacility(entityManager.createQuery("SELECT c.facilityId, COUNT(c) FROM OrderFacilityChange c "
                        + "WHERE c.changeDatetime >= :since AND c.changeReasonEnumId IN ('FCR_ROUTED', 'FCR_SPLIT', 'FCR_MANUAL') AND c.facilityId IS NOT NULL "
                        + "GROUP BY c.facilityId", Object[].class)
                .setParameter("since", since)
                .getResultList());
        Map<String, Long> shortPicksByFacility = countByFacility(entityManager.createQuery("SELECT i.facilityId, COUNT(d) FROM InventoryItemDetail d, "
                        + "InventoryItem i WHERE i.inventoryItemId = d.inventoryItemId AND d.reasonEnumId = :release AND d.description LIKE :shortPick "
                        + "AND d.effectiveDate >= :since GROUP BY i.facilityId", Object[].class)
                .setParameter("release", InventoryService.Reason.RELEASE)
                .setParameter("shortPick", SHORT_PICK_PREFIX)
                .setParameter("since", since)
                .getResultList());

        Set<String> facilityIds = new TreeSet<>(routedByFacility.keySet());
        facilityIds.addAll(shortPicksByFacility.keySet());
        List<Reliability> scores = new ArrayList<>();
        for (String facilityId : facilityIds) {
            Facility facility = entityManager.find(Facility.class, facilityId);
            if (facility == null || StoreNetworkService.VIRTUAL_FACILITY_TYPES.contains(facility.getFacilityTypeId())) {
                continue;
            }
            scores.add(reliability(facilityId, facility.getFacilityName(), routedByFacility.getOrDefault(facilityId, 0L),
                    shortPicksByFacility.getOrDefault(facilityId, 0L), days));
        }
        scores.sort(Comparator.comparingDouble(Reliability::score).thenComparing(Reliability::facilityId));
        return scores;
    }

    /** Scores for routing: location id to reliability. A location with no history is not in the map (routing uses 0.9). */
    public Map<String, Double> reliabilityScores(int days) {
        Map<String, Double> scoreByFacility = new HashMap<>();
        reliability(days).forEach(reliability -> scoreByFacility.put(reliability.facilityId(), reliability.score()));
        return scoreByFacility;
    }

    /** (picked + 9) / (orders + 10), rounded to three places, with the reason in words. */
    static Reliability reliability(String facilityId, String facilityName, long ordersRouted, long pickFailures, int days) {
        long picked = Math.max(0, ordersRouted - pickFailures);
        double score = (picked + PRIOR_SUCCESS) / (Math.max(ordersRouted, pickFailures) + PRIOR_SUCCESS + PRIOR_FAIL);
        String reason;
        if (pickFailures == 0) {
            reason = "no pick failures in " + days + " days" + (ordersRouted > 0 ? " over " + ordersRouted + " orders" : "");
        } else {
            reason = pickFailures + " pick failure(s) against " + ordersRouted + " order(s) routed in " + days + " days";
        }
        return new Reliability(facilityId, facilityName, ordersRouted, pickFailures, Math.round(score * 1000) / 1000.0, reason);
    }

    // ── phantom stock ──

    public record PhantomRisk(String productId, String facilityId, String facilityName, BigDecimal shownAvailable, double risk, List<String> reasons) {
    }

    /** A product at a location. */
    private record StockKey(String productId, String facilityId) {
    }

    /** Everything the phantom score reads, loaded once per call. */
    private record Evidence(Map<StockKey, Long> missesByStock, Map<StockKey, LocalDateTime> lastCountByStock, Map<StockKey, Long> shortCountsByStock,
                            Map<StockKey, LocalDateTime> lastShippedByStock, Map<String, LocalDateTime> lastShippedByProduct) {
    }

    /**
     * Product and location pairs most likely to show stock that isn't there, highest risk first; one location when
     * {@code facilityId} is given. Only pairs showing stock can be phantom.
     */
    public List<PhantomRisk> phantomStock(String facilityId, int days, int limit) {
        return phantomRisks(facilityId, null, days, limit);
    }

    /** Phantom risk of these products at every location (the promise service asks per order). */
    public List<PhantomRisk> phantomStock(Set<String> productIds, int days) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return phantomRisks(null, productIds, days, Integer.MAX_VALUE);
    }

    private List<PhantomRisk> phantomRisks(String facilityId, Set<String> productIds, int days, int limit) {
        LocalDateTime now = CoreClock.now();
        List<Object[]> shownStock = shownStock(facilityId, productIds);
        Evidence evidence = loadEvidence(now, now.minusDays(days));

        List<PhantomRisk> risks = new ArrayList<>();
        Map<String, Facility> facilityById = new HashMap<>();
        for (Object[] row : shownStock) {
            StockKey stock = new StockKey((String) row[0], (String) row[1]);
            BigDecimal shownQuantity = (BigDecimal) row[2];
            Facility facility = facilityById.computeIfAbsent(stock.facilityId(), id -> entityManager.find(Facility.class, id));
            if (facility == null || StoreNetworkService.VIRTUAL_FACILITY_TYPES.contains(facility.getFacilityTypeId())) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            double risk = scorePhantomRisk(stock, shownQuantity, evidence, now, days, reasons);
            if (risk > 0) {
                risks.add(new PhantomRisk(stock.productId(), stock.facilityId(), facility.getFacilityName(), shownQuantity,
                        Math.round(risk * 100) / 100.0, reasons));
            }
        }
        risks.sort(Comparator.comparingDouble(PhantomRisk::risk).reversed()
                .thenComparing(PhantomRisk::facilityId)
                .thenComparing(PhantomRisk::productId));
        return risks.size() > limit ? risks.subList(0, limit) : risks;
    }

    /**
     * The phantom risk of one product at one location, from 0 to 1, adding a reason for each part that counts:
     * <ul>
     *   <li>could not be found in the window: 0.35 each, at most 0.6;</li>
     *   <li>counts that found less than shown in 180 days: 0.15 each, at most 0.3;</li>
     *   <li>never counted, or not for 90 days: 0.1; counted in the last 14 days with no shortfall: -0.15;</li>
     *   <li>sold elsewhere in the last 30 days but not shipped from here in 60: 0.15;</li>
     *   <li>two or fewer shown: 0.05.</li>
     * </ul>
     */
    private static double scorePhantomRisk(StockKey stock, BigDecimal shownQuantity, Evidence evidence, LocalDateTime now, int days, List<String> reasons) {
        double risk = 0;

        long misses = evidence.missesByStock().getOrDefault(stock, 0L);
        if (misses > 0) {
            risk += Math.min(MAX_FOR_MISSES, PER_MISS * misses);
            reasons.add("could not be found " + misses + " time(s) in " + days + " days");
        }

        long shortCounts = evidence.shortCountsByStock().getOrDefault(stock, 0L);
        if (shortCounts > 0) {
            risk += Math.min(MAX_FOR_SHORT_COUNTS, PER_SHORT_COUNT * shortCounts);
            reasons.add(shortCounts + " count(s) found less than shown in " + SHORT_COUNT_DAYS + " days");
        }

        LocalDateTime lastCounted = evidence.lastCountByStock().get(stock);
        if (lastCounted == null) {
            risk += NEVER_OR_LONG_AGO_COUNTED;
            reasons.add("never counted");
        } else {
            long daysSinceCount = Duration.between(lastCounted, now).toDays();
            if (daysSinceCount > STALE_COUNT_DAYS) {
                risk += NEVER_OR_LONG_AGO_COUNTED;
                reasons.add("last counted " + daysSinceCount + " days ago");
            } else if (daysSinceCount <= RECENT_COUNT_DAYS && shortCounts == 0) {
                risk += RECENT_CLEAN_COUNT;   // a clean recent count is the best evidence the stock is there
                reasons.add("counted " + daysSinceCount + " day(s) ago and matched");
            }
        }

        LocalDateTime shippedHere = evidence.lastShippedByStock().get(stock);
        LocalDateTime shippedAnywhere = evidence.lastShippedByProduct().get(stock.productId());
        boolean sellingElsewhere = shippedAnywhere != null && Duration.between(shippedAnywhere, now).toDays() <= SELLING_ELSEWHERE_DAYS;
        boolean notShippingHere = shippedHere == null || Duration.between(shippedHere, now).toDays() > NOT_SHIPPED_HERE_DAYS;
        if (sellingElsewhere && notShippingHere) {
            risk += SELLS_ELSEWHERE_NOT_HERE;
            reasons.add("sells elsewhere but has not shipped from here in " + NOT_SHIPPED_HERE_DAYS + " days");
        }

        if (shownQuantity.compareTo(VERY_LOW_SHOWN) <= 0) {
            risk += VERY_LOW_QUANTITY;
            reasons.add("only " + shownQuantity.stripTrailingZeros().toPlainString() + " shown");
        }
        return Math.max(0, Math.min(1, risk));
    }

    /** Product, facility and quantity of every pair showing stock, filtered to one facility and/or some products. */
    private List<Object[]> shownStock(String facilityId, Set<String> productIds) {
        String facilityFilter = facilityId == null ? "" : " AND pf.facilityId = :facilityId";
        String productFilter = productIds == null ? "" : " AND pf.productId IN :productIds";
        TypedQuery<Object[]> query = entityManager.createQuery("SELECT pf.productId, pf.facilityId, pf.lastInventoryCount FROM ProductFacility pf "
                + "WHERE pf.lastInventoryCount > 0" + facilityFilter + productFilter, Object[].class);
        if (facilityId != null) {
            query.setParameter("facilityId", facilityId);
        }
        if (productIds != null) {
            query.setParameter("productIds", productIds);
        }
        return query.getResultList();
    }

    private Evidence loadEvidence(LocalDateTime now, LocalDateTime windowStart) {
        Map<StockKey, Long> misses = countByStock(entityManager.createQuery("SELECT i.productId, i.facilityId, COUNT(d) FROM InventoryItemDetail d, "
                        + "InventoryItem i WHERE i.inventoryItemId = d.inventoryItemId AND d.reasonEnumId = :release AND d.description LIKE :shortPick "
                        + "AND d.effectiveDate >= :since GROUP BY i.productId, i.facilityId", Object[].class)
                .setParameter("release", InventoryService.Reason.RELEASE)
                .setParameter("shortPick", SHORT_PICK_PREFIX)
                .setParameter("since", windowStart)
                .getResultList());
        Map<StockKey, LocalDateTime> lastCount = dateByStock(entityManager.createQuery("SELECT i.productId, i.facilityId, MAX(c.physicalInventoryDate) "
                        + "FROM InventoryItemVariance v, PhysicalInventory c, InventoryItem i WHERE c.physicalInventoryId = v.physicalInventoryId "
                        + "AND i.inventoryItemId = v.inventoryItemId GROUP BY i.productId, i.facilityId", Object[].class)
                .getResultList());
        Map<StockKey, Long> shortCounts = countByStock(entityManager.createQuery("SELECT i.productId, i.facilityId, COUNT(v) FROM InventoryItemVariance v, "
                        + "PhysicalInventory c, InventoryItem i WHERE c.physicalInventoryId = v.physicalInventoryId AND i.inventoryItemId = v.inventoryItemId "
                        + "AND v.quantityOnHandVar < 0 AND c.physicalInventoryDate >= :since GROUP BY i.productId, i.facilityId", Object[].class)
                .setParameter("since", now.minusDays(SHORT_COUNT_DAYS))
                .getResultList());
        Map<StockKey, LocalDateTime> lastShipped = dateByStock(entityManager.createQuery("SELECT i.productId, i.facilityId, MAX(d.effectiveDate) "
                        + "FROM InventoryItemDetail d, InventoryItem i WHERE i.inventoryItemId = d.inventoryItemId AND d.reasonEnumId = :shipment "
                        + "GROUP BY i.productId, i.facilityId", Object[].class)
                .setParameter("shipment", InventoryService.Reason.SHIPMENT)
                .getResultList());

        Map<String, LocalDateTime> lastShippedByProduct = new HashMap<>();
        lastShipped.forEach((stock, shippedAt) -> lastShippedByProduct.merge(stock.productId(), shippedAt, (first, second) -> first.isAfter(second) ? first : second));
        return new Evidence(misses, lastCount, shortCounts, lastShipped, lastShippedByProduct);
    }

    // ── reading grouped rows ──

    /** Rows of (facility id, count). */
    private static Map<String, Long> countByFacility(List<Object[]> rows) {
        Map<String, Long> countByFacility = new HashMap<>();
        for (Object[] row : rows) {
            countByFacility.put((String) row[0], ((Number) row[1]).longValue());
        }
        return countByFacility;
    }

    /** Rows of (product id, facility id, count). */
    private static Map<StockKey, Long> countByStock(List<Object[]> rows) {
        Map<StockKey, Long> countByStock = new HashMap<>();
        for (Object[] row : rows) {
            countByStock.put(new StockKey((String) row[0], (String) row[1]), ((Number) row[2]).longValue());
        }
        return countByStock;
    }

    /** Rows of (product id, facility id, date); rows without a date are left out. */
    private static Map<StockKey, LocalDateTime> dateByStock(List<Object[]> rows) {
        Map<StockKey, LocalDateTime> dateByStock = new HashMap<>();
        for (Object[] row : rows) {
            if (row[2] != null) {
                dateByStock.put(new StockKey((String) row[0], (String) row[1]), (LocalDateTime) row[2]);
            }
        }
        return dateByStock;
    }
}
