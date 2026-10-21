package com.nexus.core.service;

import com.nexus.core.model.facility.Facility;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Store reliability and phantom stock (doc 25, features 7 and 8). Both are transparent scores built from the history
 * the core already keeps, so a manager can see why: they are the rule-based fallback the doc plans for, and the place a
 * trained model's score will plug in.
 * <ul>
 *   <li>Reliability: the share of orders routed to a location that it picked, smoothed towards 90% so a location with
 *       few orders is not judged on two of them. Routing can sort by it.</li>
 *   <li>Phantom stock: per product and location, how likely the stock shown is not really there: recent failures to
 *       find it, shortfalls in recent counts, how long since it was counted, stock that does not move while other
 *       locations sell it, and very low quantities. It never changes stock; it ranks where a targeted count pays.</li>
 * </ul>
 */
@Service("coreStoreHealthService")
@Transactional(readOnly = true)
public class StoreHealthService {

    static final double PRIOR_SUCCESS = 9, PRIOR_FAIL = 1;   // a 90% prior worth 10 orders
    static final Set<String> VIRTUAL = Set.of("VIRTUAL_FACILITY", "IN_TRANSIT", "DROPSHIP");

    private final EntityManager em;

    public StoreHealthService(EntityManager em) {
        this.em = em;
    }

    public record Reliability(String facilityId, String facilityName, long ordersRouted, long pickFailures, double score, String reason) {
    }

    /** Reliability of every location that had orders or failures in the window, least reliable first. */
    public List<Reliability> reliability(int days) {
        LocalDateTime since = CoreClock.now().minusDays(days);
        Map<String, Long> routed = counts(em.createQuery("SELECT c.facilityId, COUNT(c) FROM OrderFacilityChange c WHERE c.changeDatetime >= :d "
                + "AND c.changeReasonEnumId IN ('FCR_ROUTED', 'FCR_SPLIT', 'FCR_MANUAL') AND c.facilityId IS NOT NULL GROUP BY c.facilityId", Object[].class)
                .setParameter("d", since).getResultList());
        Map<String, Long> failed = shortPicksByFacility(since);
        List<Reliability> out = new ArrayList<>();
        for (String f : union(routed.keySet(), failed.keySet())) {
            Facility fac = em.find(Facility.class, f);
            if (fac == null || VIRTUAL.contains(fac.getFacilityTypeId())) continue;
            out.add(reliability(f, fac.getFacilityName(), routed.getOrDefault(f, 0L), failed.getOrDefault(f, 0L), days));
        }
        out.sort(Comparator.comparingDouble(Reliability::score).thenComparing(Reliability::facilityId));
        return out;
    }

    /** Scores for routing: location id to reliability, 0.9 for a location with no history. */
    public Map<String, Double> reliabilityScores(int days) {
        Map<String, Double> m = new HashMap<>();
        reliability(days).forEach(r -> m.put(r.facilityId(), r.score()));
        return m;
    }

    static Reliability reliability(String facilityId, String name, long routed, long failed, int days) {
        long picked = Math.max(0, routed - failed);
        double score = (picked + PRIOR_SUCCESS) / (Math.max(routed, failed) + PRIOR_SUCCESS + PRIOR_FAIL);
        String reason = failed == 0 ? "no pick failures in " + days + " days" + (routed > 0 ? " over " + routed + " orders" : "")
                : failed + " pick failure(s) against " + routed + " order(s) routed in " + days + " days";
        return new Reliability(facilityId, name, routed, failed, Math.round(score * 1000) / 1000.0, reason);
    }

    public record PhantomRisk(String productId, String facilityId, String facilityName, BigDecimal shownAvailable, double risk, List<String> reasons) {
    }

    /**
     * Product and location pairs most likely to show stock that isn't there, highest risk first; one location when
     * {@code facilityId} is given. Only pairs showing stock can be phantom.
     */
    public List<PhantomRisk> phantomStock(String facilityId, int days, int limit) {
        return phantom(facilityId, null, days, limit);
    }

    /** Phantom risk of these products at every location (the promise service asks per order). */
    public List<PhantomRisk> phantomStock(Set<String> productIds, int days) {
        return productIds == null || productIds.isEmpty() ? List.of() : phantom(null, productIds, days, Integer.MAX_VALUE);
    }

    private List<PhantomRisk> phantom(String facilityId, Set<String> productIds, int days, int limit) {
        LocalDateTime now = CoreClock.now();
        LocalDateTime since = now.minusDays(days);
        String only = (facilityId == null ? "" : " AND pf.facilityId = :f") + (productIds == null ? "" : " AND pf.productId IN :p");
        var q = em.createQuery("SELECT pf.productId, pf.facilityId, pf.lastInventoryCount FROM ProductFacility pf WHERE pf.lastInventoryCount > 0" + only, Object[].class);
        if (facilityId != null) q.setParameter("f", facilityId);
        if (productIds != null) q.setParameter("p", productIds);
        List<Object[]> shown = q.getResultList();

        Map<String, Long> misses = counts(em.createQuery("SELECT i.productId, i.facilityId, COUNT(d) FROM InventoryItemDetail d, InventoryItem i "
                + "WHERE i.inventoryItemId = d.inventoryItemId AND d.reasonEnumId = 'IID_RELEASE' AND d.description LIKE 'Short pick%' AND d.effectiveDate >= :d "
                + "GROUP BY i.productId, i.facilityId", Object[].class).setParameter("d", since).getResultList());
        Map<String, LocalDateTime> lastCount = dates(em.createQuery("SELECT i.productId, i.facilityId, MAX(c.physicalInventoryDate) FROM InventoryItemVariance v, "
                + "PhysicalInventory c, InventoryItem i WHERE c.physicalInventoryId = v.physicalInventoryId AND i.inventoryItemId = v.inventoryItemId "
                + "GROUP BY i.productId, i.facilityId", Object[].class).getResultList());
        Map<String, Long> shortCounts = counts(em.createQuery("SELECT i.productId, i.facilityId, COUNT(v) FROM InventoryItemVariance v, PhysicalInventory c, "
                + "InventoryItem i WHERE c.physicalInventoryId = v.physicalInventoryId AND i.inventoryItemId = v.inventoryItemId AND v.quantityOnHandVar < 0 "
                + "AND c.physicalInventoryDate >= :d GROUP BY i.productId, i.facilityId", Object[].class).setParameter("d", now.minusDays(180)).getResultList());
        Map<String, LocalDateTime> lastShipped = dates(em.createQuery("SELECT i.productId, i.facilityId, MAX(d.effectiveDate) FROM InventoryItemDetail d, InventoryItem i "
                + "WHERE i.inventoryItemId = d.inventoryItemId AND d.reasonEnumId = 'IID_SHIPMENT' GROUP BY i.productId, i.facilityId", Object[].class).getResultList());
        Map<String, LocalDateTime> lastShippedAnywhere = new HashMap<>();
        lastShipped.forEach((k, v) -> lastShippedAnywhere.merge(k.substring(0, k.indexOf('|')), v, (a, b) -> a.isAfter(b) ? a : b));

        List<PhantomRisk> out = new ArrayList<>();
        Map<String, Facility> facilities = new HashMap<>();
        for (Object[] r : shown) {
            String p = (String) r[0], f = (String) r[1];
            Facility fac = facilities.computeIfAbsent(f, id -> em.find(Facility.class, id));
            if (fac == null || VIRTUAL.contains(fac.getFacilityTypeId())) continue;
            String key = p + "|" + f;
            BigDecimal qty = (BigDecimal) r[2];
            List<String> reasons = new ArrayList<>();
            double risk = 0;
            long miss = misses.getOrDefault(key, 0L);
            if (miss > 0) {
                risk += Math.min(0.6, 0.35 * miss);
                reasons.add("could not be found " + miss + " time(s) in " + days + " days");
            }
            long shortCount = shortCounts.getOrDefault(key, 0L);
            if (shortCount > 0) {
                risk += Math.min(0.3, 0.15 * shortCount);
                reasons.add(shortCount + " count(s) found less than shown in 180 days");
            }
            LocalDateTime counted = lastCount.get(key);
            long sinceCount = counted == null ? Long.MAX_VALUE : Duration.between(counted, now).toDays();
            if (counted == null) {
                risk += 0.1;
                reasons.add("never counted");
            } else if (sinceCount > 90) {
                risk += 0.1;
                reasons.add("last counted " + sinceCount + " days ago");
            } else if (sinceCount <= 14 && shortCount == 0) {
                risk -= 0.15;   // a clean recent count is the best evidence the stock is there
                reasons.add("counted " + sinceCount + " day(s) ago and matched");
            }
            LocalDateTime shippedHere = lastShipped.get(key), shippedAnywhere = lastShippedAnywhere.get(p);
            if (shippedAnywhere != null && Duration.between(shippedAnywhere, now).toDays() <= 30
                    && (shippedHere == null || Duration.between(shippedHere, now).toDays() > 60)) {
                risk += 0.15;
                reasons.add("sells elsewhere but has not shipped from here in 60 days");
            }
            if (qty.compareTo(BigDecimal.valueOf(2)) <= 0) {
                risk += 0.05;
                reasons.add("only " + qty.stripTrailingZeros().toPlainString() + " shown");
            }
            risk = Math.max(0, Math.min(1, risk));
            if (risk > 0) out.add(new PhantomRisk(p, f, fac.getFacilityName(), qty, Math.round(risk * 100) / 100.0, reasons));
        }
        out.sort(Comparator.comparingDouble(PhantomRisk::risk).reversed().thenComparing(PhantomRisk::facilityId).thenComparing(PhantomRisk::productId));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    // ── helpers ──

    private Map<String, Long> shortPicksByFacility(LocalDateTime since) {
        return counts(em.createQuery("SELECT i.facilityId, COUNT(d) FROM InventoryItemDetail d, InventoryItem i WHERE i.inventoryItemId = d.inventoryItemId "
                + "AND d.reasonEnumId = 'IID_RELEASE' AND d.description LIKE 'Short pick%' AND d.effectiveDate >= :d GROUP BY i.facilityId", Object[].class)
                .setParameter("d", since).getResultList());
    }

    /** Rows whose last column is a count and the rest the key, joined with '|'. */
    private static Map<String, Long> counts(List<Object[]> rows) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put(key(r), ((Number) r[r.length - 1]).longValue());
        return m;
    }

    private static Map<String, LocalDateTime> dates(List<Object[]> rows) {
        Map<String, LocalDateTime> m = new HashMap<>();
        for (Object[] r : rows) if (r[r.length - 1] != null) m.put(key(r), (LocalDateTime) r[r.length - 1]);
        return m;
    }

    private static String key(Object[] r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length - 1; i++) sb.append(i == 0 ? "" : "|").append(r[i]);
        return sb.toString();
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.TreeSet<String> s = new java.util.TreeSet<>(a);
        s.addAll(b);
        return s;
    }
}
