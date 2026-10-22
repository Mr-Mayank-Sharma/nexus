package com.nexus.core.service;

import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.party.PostalAddress;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The availability and promise service (doc 25, features 2 and 4): one answer to "can you deliver this, here, by
 * then?". For each location that can fill the whole order:
 * <ul>
 *   <li>when it would ship: today before its cut-off and while it has capacity, otherwise the next working day;</li>
 *   <li>how long delivery takes: from the location's own history of deliveries to a similar distance when there are
 *       enough of them (expected = median, promise = 90th percentile: late costs trust), otherwise from distance
 *       rules;</li>
 *   <li>how sure we are: confidence falls (and the promise moves a day) when the stock may not be there (phantom risk)
 *       or the location often fails to pick.</li>
 * </ul>
 */
@Service("corePromiseService")
@CoreTransactional(readOnly = true)
public class PromiseService {

    /** Fewer past deliveries than this, and the distance rules are used instead. */
    static final int MIN_HISTORY = 20;
    static final int MAX_HISTORY_ROWS = 2000;
    static final LocalTime DEFAULT_CUTOFF = LocalTime.of(15, 0);
    static final String DEFAULT_METHOD = "STANDARD";
    static final double LIKELY_PHANTOM = 0.5;
    static final double UNRELIABLE = 0.8;
    static final int RELIABILITY_DAYS = 90;
    static final int PHANTOM_DAYS = 30;

    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";

    private final EntityManager entityManager;
    private final FacilityService facilityService;
    private final StoreHealthService storeHealthService;

    public PromiseService(EntityManager entityManager, FacilityService facilityService, StoreHealthService storeHealthService) {
        this.entityManager = entityManager;
        this.facilityService = facilityService;
        this.storeHealthService = storeHealthService;
    }

    public record Destination(Double latitude, Double longitude, String postalCode, String countryCode) {
        public static Destination of(PostalAddress address) {
            Double latitude = address.getLatitude() == null ? null : address.getLatitude().doubleValue();
            Double longitude = address.getLongitude() == null ? null : address.getLongitude().doubleValue();
            return new Destination(latitude, longitude, address.getPostalCode(), null);
        }
    }

    /** One location's promise. {@code basis} is "history" or "rule"; {@code confidence} is HIGH, MEDIUM or LOW. */
    public record Option(String facilityId, String facilityName, LocalDate shipDate, LocalDate expectedDelivery, LocalDate promiseBy, int transitDaysExpected,
                         int transitDaysPromise, String basis, String confidence, Double distanceKm, List<String> reasons) {
    }

    public record Answer(boolean deliverable, Option best, List<Option> options, String summary) {
    }

    /** Working days in transit: expected and promised, and how many past deliveries they came from (0 for a rule). */
    record Transit(int expectedDays, int promiseDays, int samples) {
    }

    /** Where and when the products (product id to quantity) can be delivered, earliest promise first. */
    public Answer promise(Map<String, BigDecimal> quantityByProduct, Destination destination, String shipmentMethodTypeId, LocalDateTime at) {
        if (quantityByProduct == null || quantityByProduct.isEmpty()) {
            throw new IllegalArgumentException("Name at least one product and quantity");
        }
        LocalDateTime now = at == null ? CoreClock.now() : at;
        String method = shipmentMethodTypeId == null ? DEFAULT_METHOD : shipmentMethodTypeId;

        // What each location may promise of each product.
        Map<String, Map<String, BigDecimal>> stockByFacility = new HashMap<>();
        entityManager.createQuery("SELECT pf.facilityId, pf.productId, pf.computedLastInventoryCount FROM ProductFacility pf WHERE pf.productId IN :productIds "
                        + "AND (pf.allowBrokering IS NULL OR pf.allowBrokering = true)", Object[].class)
                .setParameter("productIds", quantityByProduct.keySet())
                .getResultList()
                .forEach(row -> stockByFacility.computeIfAbsent((String) row[0], facilityId -> new HashMap<>())
                        .put((String) row[1], row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]));

        Map<String, Double> reliabilityByFacility = storeHealthService.reliabilityScores(RELIABILITY_DAYS);
        Map<String, Double> phantomRiskByFacility = new HashMap<>();
        for (StoreHealthService.PhantomRisk risk : storeHealthService.phantomStock(quantityByProduct.keySet(), PHANTOM_DAYS)) {
            phantomRiskByFacility.merge(risk.facilityId(), risk.risk(), Math::max);   // the riskiest product decides
        }

        List<Option> options = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : stockByFacility.entrySet()) {
            Map<String, BigDecimal> stock = entry.getValue();
            boolean fillsEverything = quantityByProduct.entrySet().stream()
                    .allMatch(wanted -> stock.getOrDefault(wanted.getKey(), BigDecimal.ZERO).compareTo(wanted.getValue()) >= 0);
            if (!fillsEverything) {
                continue;
            }
            Facility facility = entityManager.find(Facility.class, entry.getKey());
            if (facility == null || StoreNetworkService.VIRTUAL_FACILITY_TYPES.contains(facility.getFacilityTypeId())) {
                continue;
            }
            boolean closed = facility.getClosedDate() != null && !facility.getClosedDate().isAfter(now);
            if (closed) {
                continue;
            }
            double reliability = reliabilityByFacility.getOrDefault(facility.getFacilityId(), CandidateLocation.DEFAULT_RELIABILITY);
            double phantomRisk = phantomRiskByFacility.getOrDefault(facility.getFacilityId(), 0.0);
            options.add(optionFor(facility, destination, method, now, reliability, phantomRisk));
        }

        options.sort(Comparator.comparing(Option::promiseBy)
                .thenComparing(option -> confidenceRank(option.confidence()))
                .thenComparing(Option::facilityId));
        if (options.isEmpty()) {
            return new Answer(false, null, List.of(), "No single location holds all of it now");
        }
        Option best = options.get(0);
        String summary = "Delivered by " + best.promiseBy() + " (expected " + best.expectedDelivery() + ") from " + best.facilityName()
                + ", " + best.confidence().toLowerCase(Locale.ROOT) + " confidence";
        return new Answer(true, best, options, summary);
    }

    /** Where a saved postal address is, as a promise destination. */
    public Destination destinationOf(String contactMechId) {
        PostalAddress address = entityManager.find(PostalAddress.class, contactMechId);
        if (address == null) {
            throw new IllegalArgumentException("Unknown address " + contactMechId);
        }
        return Destination.of(address);
    }

    /** One location's ship date, delivery dates and confidence, with the reasons. */
    private Option optionFor(Facility facility, Destination destination, String method, LocalDateTime now, double reliability, double phantomRisk) {
        List<String> reasons = new ArrayList<>();

        // Step 1: the ship date, in the location's own time zone.
        ZoneId zone = zoneOf(facility.getFacilityTimeZone());
        LocalDateTime localNow = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime();
        LocalTime cutoff = facility.getCloseTime() != null ? facility.getCloseTime().minusHours(1) : DEFAULT_CUTOFF;
        LocalDate today = localNow.toLocalDate();
        LocalDate shipDate = today;
        if (!isWorkday(today) || !localNow.toLocalTime().isBefore(cutoff)) {
            shipDate = nextWorkday(today);
            reasons.add("past today's " + cutoff + " cut-off; ships " + shipDate);
        }
        if (shipDate.equals(today)) {
            Optional<Long> ordersLeft = facilityService.ordersLeft(facility.getFacilityId(), shipDate);
            if (ordersLeft.isPresent() && ordersLeft.get() <= 0) {
                shipDate = nextWorkday(shipDate);
                reasons.add("full for today; ships " + shipDate);
            }
        }

        // Step 2: time in transit, from history or from the distance rules.
        Double distanceKm = distanceKm(facility, destination);
        Transit transit = fromHistory(facility.getFacilityId(), distanceKm, method);
        String basis;
        if (transit != null) {
            basis = "history";
            reasons.add("from " + transit.samples() + " past deliveries from here at a similar distance");
        } else {
            transit = fromRule(distanceKm, method);
            basis = "rule";
            reasons.add(distanceKm == null ? "no map position; a cautious rule" : String.format(Locale.ROOT, "%.0f km; a distance rule", distanceKm));
        }
        LocalDate expectedDelivery = addWorkdays(shipDate, transit.expectedDays());
        LocalDate promiseBy = addWorkdays(shipDate, transit.promiseDays());

        // Step 3: confidence. A doubtful location's promise moves a day.
        String confidence = "history".equals(basis) ? HIGH : MEDIUM;
        if (phantomRisk >= LIKELY_PHANTOM) {
            confidence = LOW;
            reasons.add("the stock there may not be real");
            promiseBy = addWorkdays(promiseBy, 1);
        } else if (reliability < UNRELIABLE) {
            confidence = LOW;
            reasons.add(String.format(Locale.ROOT, "it picks only %.0f%% of its orders", reliability * 100));
            promiseBy = addWorkdays(promiseBy, 1);
        }
        return new Option(facility.getFacilityId(), facility.getFacilityName(), shipDate, expectedDelivery, promiseBy, transit.expectedDays(),
                transit.promiseDays(), basis, confidence, distanceKm, reasons);
    }

    /**
     * Transit from past deliveries of this location, by this method, within the same distance band; null when there is
     * no distance or fewer than {@value #MIN_HISTORY} such deliveries.
     */
    Transit fromHistory(String facilityId, Double distanceKm, String method) {
        if (distanceKm == null) {
            return null;
        }
        List<Object[]> deliveries = entityManager.createQuery("SELECT r.actualStartDate, r.actualArrivalDate, a.latitude, a.longitude "
                        + "FROM ShipmentRouteSegment r, Shipment s, PostalAddress a WHERE s.shipmentId = r.shipmentId "
                        + "AND a.contactMechId = s.destinationContactMechId AND s.originFacilityId = :facilityId AND s.shipmentTypeId = 'SALES_SHIPMENT' "
                        + "AND r.actualStartDate IS NOT NULL AND r.actualArrivalDate IS NOT NULL AND a.latitude IS NOT NULL "
                        + "AND (r.shipmentMethodTypeId = :method OR r.shipmentMethodTypeId IS NULL) ORDER BY r.actualArrivalDate DESC", Object[].class)
                .setParameter("facilityId", facilityId)
                .setParameter("method", method)
                .setMaxResults(MAX_HISTORY_ROWS)
                .getResultList();
        Facility facility = entityManager.find(Facility.class, facilityId);
        int band = distanceBand(distanceKm);
        List<Integer> transitDays = new ArrayList<>();
        for (Object[] delivery : deliveries) {
            double deliveryKm = GeoDistance.km(facility.getLatitude().doubleValue(), facility.getLongitude().doubleValue(),
                    ((BigDecimal) delivery[2]).doubleValue(), ((BigDecimal) delivery[3]).doubleValue());
            if (distanceBand(deliveryKm) != band) {
                continue;
            }
            LocalDate shipped = ((LocalDateTime) delivery[0]).toLocalDate();
            LocalDate arrived = ((LocalDateTime) delivery[1]).toLocalDate();
            transitDays.add(workdaysBetween(shipped, arrived));
        }
        if (transitDays.size() < MIN_HISTORY) {
            return null;
        }
        transitDays.sort(Integer::compare);
        return new Transit(quantile(transitDays, 0.5), quantile(transitDays, 0.9), transitDays.size());
    }

    /** Transit by distance and method when there is no history. */
    static Transit fromRule(Double distanceKm, String method) {
        if ("SAME_DAY".equals(method) && distanceKm != null && distanceKm <= 30) {
            return new Transit(0, 0, 0);
        }
        if ("NEXT_DAY".equals(method) || "EXPRESS".equals(method)) {
            return new Transit(1, 2, 0);
        }
        if ("STOREPICKUP".equals(method)) {
            return new Transit(0, 1, 0);
        }
        if (distanceKm == null) {
            return new Transit(4, 7, 0);
        }
        if (distanceKm <= 50) {
            return new Transit(1, 2, 0);
        }
        if (distanceKm <= 500) {
            return new Transit(2, 3, 0);
        }
        if (distanceKm <= 1500) {
            return new Transit(3, 5, 0);
        }
        return new Transit(5, 8, 0);
    }

    /** 0: up to 50 km, 1: up to 500, 2: up to 1500, 3: further. */
    static int distanceBand(double distanceKm) {
        if (distanceKm <= 50) {
            return 0;
        }
        if (distanceKm <= 500) {
            return 1;
        }
        return distanceKm <= 1500 ? 2 : 3;
    }

    /** The value at the quantile of a sorted list (nearest rank). */
    static int quantile(List<Integer> sortedValues, double quantile) {
        int rank = (int) Math.ceil(quantile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(sortedValues.size() - 1, rank)));
    }

    private static Double distanceKm(Facility facility, Destination destination) {
        boolean bothPlaced = destination != null && destination.latitude() != null && destination.longitude() != null
                && facility.getLatitude() != null && facility.getLongitude() != null;
        if (!bothPlaced) {
            return null;
        }
        return GeoDistance.km(facility.getLatitude().doubleValue(), facility.getLongitude().doubleValue(), destination.latitude(), destination.longitude());
    }

    // ── working days ──

    static boolean isWorkday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    static LocalDate nextWorkday(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isWorkday(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    static LocalDate addWorkdays(LocalDate date, int workdays) {
        LocalDate result = date;
        for (int added = 0; added < workdays; added++) {
            result = nextWorkday(result);
        }
        return result;
    }

    /** Working days after {@code from}, up to and including {@code to}. */
    static int workdaysBetween(LocalDate from, LocalDate to) {
        int workdays = 0;
        for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
            if (isWorkday(day.plusDays(1))) {
                workdays++;
            }
        }
        return workdays;
    }

    private static ZoneId zoneOf(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException unknownZone) {
            return ZoneOffset.UTC;
        }
    }

    private static int confidenceRank(String confidence) {
        if (HIGH.equals(confidence)) {
            return 0;
        }
        return MEDIUM.equals(confidence) ? 1 : 2;
    }
}
