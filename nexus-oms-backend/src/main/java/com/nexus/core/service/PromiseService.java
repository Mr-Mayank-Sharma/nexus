package com.nexus.core.service;

import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.party.PostalAddress;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The availability and promise service (doc 25, features 2 and 4): one answer to "can you deliver this, here, by
 * then?". For each location that can fill the whole order: when it would ship (today before its cut-off and while it
 * has capacity, otherwise the next working day), how long delivery takes, and how sure we are. Delivery time comes from
 * the location's own history of deliveries to a similar distance when there are enough of them (expected = median,
 * promise = 90th percentile: late costs trust), otherwise from distance rules. Confidence falls when the stock may
 * not be there (phantom risk) or the location often fails to pick.
 */
@Service("corePromiseService")
@Transactional(readOnly = true)
public class PromiseService {

    static final int MIN_HISTORY = 20;
    static final LocalTime DEFAULT_CUTOFF = LocalTime.of(15, 0);
    static final Set<String> VIRTUAL = Set.of("VIRTUAL_FACILITY", "IN_TRANSIT", "DROPSHIP");

    private final EntityManager em;
    private final FacilityService facilities;
    private final StoreHealthService health;

    public PromiseService(EntityManager em, FacilityService facilities, StoreHealthService health) {
        this.em = em;
        this.facilities = facilities;
        this.health = health;
    }

    public record Destination(Double latitude, Double longitude, String postalCode, String countryCode) {
        public static Destination of(PostalAddress a) {
            return new Destination(a.getLatitude() == null ? null : a.getLatitude().doubleValue(), a.getLongitude() == null ? null : a.getLongitude().doubleValue(),
                    a.getPostalCode(), null);
        }
    }

    public record Option(String facilityId, String facilityName, LocalDate shipDate, LocalDate expectedDelivery, LocalDate promiseBy, int transitDaysExpected,
                         int transitDaysPromise, String basis, String confidence, Double distanceKm, List<String> reasons) {
    }

    public record Answer(boolean deliverable, Option best, List<Option> options, String summary) {
    }

    /** Where and when the products (product id to quantity) can be delivered, earliest promise first. */
    public Answer promise(Map<String, BigDecimal> products, Destination to, String shipmentMethodTypeId, LocalDateTime at) {
        if (products == null || products.isEmpty()) throw new IllegalArgumentException("Name at least one product and quantity");
        LocalDateTime now = at == null ? CoreClock.now() : at;
        String method = shipmentMethodTypeId == null ? "STANDARD" : shipmentMethodTypeId;
        Map<String, Map<String, BigDecimal>> stock = new HashMap<>();
        em.createQuery("SELECT pf.facilityId, pf.productId, pf.computedLastInventoryCount FROM ProductFacility pf WHERE pf.productId IN :p "
                        + "AND (pf.allowBrokering IS NULL OR pf.allowBrokering = true)", Object[].class)
                .setParameter("p", products.keySet()).getResultList()
                .forEach(r -> stock.computeIfAbsent((String) r[0], k -> new HashMap<>()).put((String) r[1], r[2] == null ? BigDecimal.ZERO : (BigDecimal) r[2]));
        Map<String, Double> reliability = health.reliabilityScores(90);
        Map<String, Double> phantom = new HashMap<>();
        for (StoreHealthService.PhantomRisk p : health.phantomStock(products.keySet(), 30)) phantom.merge(p.facilityId(), p.risk(), Math::max);

        List<Option> options = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> e : stock.entrySet()) {
            boolean fills = products.entrySet().stream().allMatch(p -> e.getValue().getOrDefault(p.getKey(), BigDecimal.ZERO).compareTo(p.getValue()) >= 0);
            if (!fills) continue;
            Facility f = em.find(Facility.class, e.getKey());
            if (f == null || VIRTUAL.contains(f.getFacilityTypeId())) continue;
            if (f.getClosedDate() != null && !f.getClosedDate().isAfter(now)) continue;
            options.add(option(f, to, method, now, reliability.getOrDefault(f.getFacilityId(), 0.9), phantom.getOrDefault(f.getFacilityId(), 0.0)));
        }
        options.sort(Comparator.comparing(Option::promiseBy).thenComparing(o -> confidenceRank(o.confidence())).thenComparing(Option::facilityId));
        if (options.isEmpty()) return new Answer(false, null, List.of(), "No single location holds all of it now");
        Option best = options.get(0);
        return new Answer(true, best, options, "Delivered by " + best.promiseBy() + " (expected " + best.expectedDelivery() + ") from " + best.facilityName()
                + ", " + best.confidence().toLowerCase() + " confidence");
    }

    private Option option(Facility f, Destination to, String method, LocalDateTime now, double reliability, double phantomRisk) {
        List<String> reasons = new ArrayList<>();
        ZoneId zone = zone(f.getFacilityTimeZone());
        LocalDateTime local = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime();
        LocalTime cutoff = f.getCloseTime() != null ? f.getCloseTime().minusHours(1) : DEFAULT_CUTOFF;
        LocalDate ship = local.toLocalDate();
        if (!isWorkday(ship) || !local.toLocalTime().isBefore(cutoff)) {
            ship = nextWorkday(ship);
            reasons.add("past today's " + cutoff + " cut-off; ships " + ship);
        }
        if (ship.equals(local.toLocalDate())) {
            var left = facilities.ordersLeft(f.getFacilityId(), ship);
            if (left.isPresent() && left.get() <= 0) {
                ship = nextWorkday(ship);
                reasons.add("full for today; ships " + ship);
            }
        }
        Double km = distance(f, to);
        int[] transit = history(f.getFacilityId(), km, method);
        String basis;
        if (transit != null) {
            basis = "history";
            reasons.add("from " + transit[2] + " past deliveries from here at a similar distance");
        } else {
            transit = rule(km, method);
            basis = "rule";
            reasons.add(km == null ? "no map position; a cautious rule" : String.format(java.util.Locale.ROOT, "%.0f km; a distance rule", km));
        }
        LocalDate expected = addWorkdays(ship, transit[0]);
        LocalDate by = addWorkdays(ship, transit[1]);
        String confidence = "history".equals(basis) ? "HIGH" : "MEDIUM";
        if (phantomRisk >= 0.5) {
            confidence = "LOW";
            reasons.add("the stock there may not be real");
            by = addWorkdays(by, 1);
        } else if (reliability < 0.8) {
            confidence = "LOW";
            reasons.add(String.format(java.util.Locale.ROOT, "it picks only %.0f%% of its orders", reliability * 100));
            by = addWorkdays(by, 1);
        }
        return new Option(f.getFacilityId(), f.getFacilityName(), ship, expected, by, transit[0], transit[1], basis, confidence, km, reasons);
    }

    /** {expected, promise, samples} working days from past deliveries of this location within a similar distance band, or null. */
    int[] history(String facilityId, Double km, String method) {
        if (km == null) return null;
        List<Object[]> rows = em.createQuery("SELECT r.actualStartDate, r.actualArrivalDate, a.latitude, a.longitude FROM ShipmentRouteSegment r, Shipment s, PostalAddress a "
                        + "WHERE s.shipmentId = r.shipmentId AND a.contactMechId = s.destinationContactMechId AND s.originFacilityId = :f AND s.shipmentTypeId = 'SALES_SHIPMENT' "
                        + "AND r.actualStartDate IS NOT NULL AND r.actualArrivalDate IS NOT NULL AND a.latitude IS NOT NULL AND (r.shipmentMethodTypeId = :m OR r.shipmentMethodTypeId IS NULL) "
                        + "ORDER BY r.actualArrivalDate DESC", Object[].class)
                .setParameter("f", facilityId).setParameter("m", method).setMaxResults(2000).getResultList();
        Facility f = em.find(Facility.class, facilityId);
        int band = band(km);
        List<Integer> days = new ArrayList<>();
        for (Object[] r : rows) {
            double d = GeoDistance.km(f.getLatitude().doubleValue(), f.getLongitude().doubleValue(), ((BigDecimal) r[2]).doubleValue(), ((BigDecimal) r[3]).doubleValue());
            if (band(d) != band) continue;
            days.add(workdaysBetween(((LocalDateTime) r[0]).toLocalDate(), ((LocalDateTime) r[1]).toLocalDate()));
        }
        if (days.size() < MIN_HISTORY) return null;
        days.sort(Integer::compare);
        return new int[]{quantile(days, 0.5), quantile(days, 0.9), days.size()};
    }

    /** Working days {expected, promise} by distance and method when there is no history. */
    static int[] rule(Double km, String method) {
        if ("SAME_DAY".equals(method) && km != null && km <= 30) return new int[]{0, 0};
        if ("NEXT_DAY".equals(method) || "EXPRESS".equals(method)) return new int[]{1, 2};
        if ("STOREPICKUP".equals(method)) return new int[]{0, 1};
        if (km == null) return new int[]{4, 7};
        if (km <= 50) return new int[]{1, 2};
        if (km <= 500) return new int[]{2, 3};
        if (km <= 1500) return new int[]{3, 5};
        return new int[]{5, 8};
    }

    static int band(double km) {
        return km <= 50 ? 0 : km <= 500 ? 1 : km <= 1500 ? 2 : 3;
    }

    static int quantile(List<Integer> sorted, double q) {
        int i = (int) Math.ceil(q * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, i)));
    }

    private Double distance(Facility f, Destination to) {
        if (to == null || to.latitude() == null || to.longitude() == null || f.getLatitude() == null || f.getLongitude() == null) return null;
        return GeoDistance.km(f.getLatitude().doubleValue(), f.getLongitude().doubleValue(), to.latitude(), to.longitude());
    }

    static boolean isWorkday(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    static LocalDate nextWorkday(LocalDate d) {
        LocalDate n = d.plusDays(1);
        while (!isWorkday(n)) n = n.plusDays(1);
        return n;
    }

    static LocalDate addWorkdays(LocalDate d, int days) {
        LocalDate n = d;
        for (int i = 0; i < days; i++) n = nextWorkday(n);
        return n;
    }

    static int workdaysBetween(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) if (isWorkday(d.plusDays(1))) n++;
        return n;
    }

    private static ZoneId zone(String id) {
        try {
            return id == null || id.isBlank() ? ZoneOffset.UTC : ZoneId.of(id);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private static int confidenceRank(String c) {
        return "HIGH".equals(c) ? 0 : "MEDIUM".equals(c) ? 1 : 2;
    }
}
