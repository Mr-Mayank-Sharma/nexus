package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.inventory.ProductFacility;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How stores work with each other (doc 24, section 4b). Stores never talk directly: every lookup, hold, credit and
 * suggestion goes through the one stock record per store the OMS keeps.
 * <ul>
 *   <li>{@link #lookup}: which stores can sell a product now, and how sure we are the stock is really there;</li>
 *   <li>{@link #holdForPickup}: hold a product at another store for a customer to collect;</li>
 *   <li>{@link #salesCredit}: split an order's sales between the store that sold it and the store that shipped it;</li>
 *   <li>{@link #rebalance}: transfers worth making inside a store cluster before stock has to be marked down.</li>
 * </ul>
 */
@Service("coreStoreNetworkService")
@Transactional
public class StoreNetworkService {

    static final String RESOURCE = "nexus.core";
    /** Facility types that hold no shelf a customer could buy from. */
    static final Set<String> VIRTUAL_FACILITY_TYPES = Set.of("VIRTUAL_FACILITY", "IN_TRANSIT", "DROPSHIP");

    static final String FIRST_ITEM = SequenceService.seqId(1);
    static final String PICKUP_METHOD = "STOREPICKUP";
    static final String STORE_SALES_CHANNEL = "POS_SALES_CHANNEL";
    static final String NO_CARRIER = "_NA_";
    static final String SELLING_SHARE_SETTING = "SALES_CREDIT_SELLING_PCT";
    static final long DEFAULT_SELLING_SHARE_PERCENT = 50;

    // Confidence rules for a lookup.
    static final BigDecimal COMFORTABLE_STOCK = BigDecimal.valueOf(3);
    static final long RECENT_COUNT_DAYS = 30;
    static final long STALE_COUNT_DAYS = 90;
    static final long MISS_WINDOW_DAYS = 30;

    private final EntityManager entityManager;
    private final OrderService orderService;
    private final OrderReservationService reservationService;
    private final ProductStoreService productStoreService;
    private final SystemPropertyService systemPropertyService;

    public StoreNetworkService(EntityManager entityManager, OrderService orderService, OrderReservationService reservationService,
                               ProductStoreService productStoreService, SystemPropertyService systemPropertyService) {
        this.entityManager = entityManager;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.productStoreService = productStoreService;
        this.systemPropertyService = systemPropertyService;
    }

    // ── lookup with confidence ──

    public enum Confidence { HIGH, MEDIUM, LOW }

    public record Availability(String facilityId, String facilityName, BigDecimal sellable, Confidence confidence, List<String> reasons) {
    }

    /**
     * Which locations can sell a product now, most confident and most stock first. Each location starts at a score of
     * zero and the score moves with:
     * <ul>
     *   <li>how many are left: three or more +1, only one -2;</li>
     *   <li>when it was last counted: within 30 days +1, never -1, over 90 days ago -1;</li>
     *   <li>whether it failed to find this product in the last 30 days: -2.</li>
     * </ul>
     * Two or more is high confidence, below zero is low, anything else medium.
     */
    @Transactional(readOnly = true)
    public List<Availability> lookup(String productId) {
        LocalDateTime now = CoreClock.now();
        List<ProductFacility> stockedLocations = entityManager.createQuery(
                        "SELECT pf FROM ProductFacility pf WHERE pf.productId = :productId AND pf.computedLastInventoryCount > 0", ProductFacility.class)
                .setParameter("productId", productId)
                .getResultList();

        List<Availability> availability = new ArrayList<>();
        for (ProductFacility stock : stockedLocations) {
            Facility facility = entityManager.find(Facility.class, stock.getFacilityId());
            if (facility == null || VIRTUAL_FACILITY_TYPES.contains(facility.getFacilityTypeId())) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            int score = 0;

            BigDecimal sellable = stock.getComputedLastInventoryCount();
            if (sellable.compareTo(COMFORTABLE_STOCK) >= 0) {
                score++;
            } else if (sellable.compareTo(BigDecimal.ONE) <= 0) {
                score -= 2;
                reasons.add("only one left");
            } else {
                reasons.add("only " + sellable.stripTrailingZeros().toPlainString() + " left");
            }

            LocalDateTime lastCounted = lastCounted(productId, stock.getFacilityId());
            if (lastCounted == null) {
                score--;
                reasons.add("never counted");
            } else {
                long daysSinceCount = Duration.between(lastCounted, now).toDays();
                if (daysSinceCount <= RECENT_COUNT_DAYS) {
                    score++;
                    reasons.add("counted " + daysSinceCount + " day(s) ago");
                } else if (daysSinceCount > STALE_COUNT_DAYS) {
                    score--;
                    reasons.add("last counted " + daysSinceCount + " days ago");
                }
            }

            long recentMisses = recentMisses(productId, stock.getFacilityId(), now.minusDays(MISS_WINDOW_DAYS));
            if (recentMisses > 0) {
                score -= 2;
                reasons.add("could not find it " + recentMisses + " time(s) in " + MISS_WINDOW_DAYS + " days");
            }

            Confidence confidence = score >= 2 ? Confidence.HIGH : score <= -1 ? Confidence.LOW : Confidence.MEDIUM;
            availability.add(new Availability(stock.getFacilityId(), facility.getFacilityName(), sellable, confidence, reasons));
        }
        availability.sort(Comparator.comparing(Availability::confidence)
                .thenComparing(Availability::sellable, Comparator.reverseOrder())
                .thenComparing(Availability::facilityId));
        return availability;
    }

    /**
     * When the product was last counted at the facility. Read from the counts themselves, so a count that matched the
     * shelf (and so changed no stock) still counts.
     */
    private LocalDateTime lastCounted(String productId, String facilityId) {
        return entityManager.createQuery(
                        "SELECT MAX(stockCount.physicalInventoryDate) FROM InventoryItemVariance variance, PhysicalInventory stockCount, InventoryItem item "
                                + "WHERE stockCount.physicalInventoryId = variance.physicalInventoryId AND item.inventoryItemId = variance.inventoryItemId "
                                + "AND item.productId = :productId AND item.facilityId = :facilityId", LocalDateTime.class)
                .setParameter("productId", productId)
                .setParameter("facilityId", facilityId)
                .getSingleResult();
    }

    /** How many times since {@code since} the facility could not find this product (it was then excluded for the item). */
    private long recentMisses(String productId, String facilityId, LocalDateTime since) {
        return entityManager.createQuery(
                        "SELECT COUNT(exclusion) FROM ExcludedOrderFacility exclusion, OrderItem item WHERE item.orderId = exclusion.orderId "
                                + "AND item.orderItemSeqId = exclusion.orderItemSeqId AND exclusion.facilityId = :facilityId "
                                + "AND item.productId = :productId AND exclusion.fromDate >= :since", Long.class)
                .setParameter("facilityId", facilityId)
                .setParameter("productId", productId)
                .setParameter("since", since)
                .getSingleResult();
    }

    /** A product a location holds: what it may promise (sellable) and what it shows (available). */
    public record StockAtLocation(String productId, String productName, String sku, BigDecimal sellable, BigDecimal available) {
    }

    /** Products in stock at a location whose name or SKU contains the text (any case), by name; at most 50. */
    @Transactional(readOnly = true)
    public List<StockAtLocation> stockAt(String facilityId, String text) {
        String pattern = "%" + (text == null ? "" : text.trim().toLowerCase(Locale.ROOT)) + "%";
        return entityManager.createQuery("SELECT p.productId, p.productName, g.idValue, pf.computedLastInventoryCount, pf.lastInventoryCount "
                        + "FROM ProductFacility pf, Product p, GoodIdentification g WHERE p.productId = pf.productId AND g.productId = p.productId "
                        + "AND g.goodIdentificationTypeId = 'SKU' AND g.thruDate IS NULL AND pf.facilityId = :facilityId AND pf.lastInventoryCount > 0 "
                        + "AND (LOWER(p.productName) LIKE :pattern OR LOWER(g.idValue) LIKE :pattern) ORDER BY p.productName", Object[].class)
                .setParameter("facilityId", facilityId)
                .setParameter("pattern", pattern)
                .setMaxResults(50)
                .getResultList()
                .stream()
                .map(row -> new StockAtLocation((String) row[0], row[1] == null ? "" : (String) row[1], (String) row[2], (BigDecimal) row[3],
                        (BigDecimal) row[4]))
                .toList();
    }

    // ── hold at another store for pickup ──

    /**
     * Holds a product at a store for a customer to collect:
     * <ol>
     *   <li>an approved pickup order at that store, for the product, at no charge yet;</li>
     *   <li>the stock held there at once;</li>
     *   <li>the item set to cancel itself (releasing the stock) when the hold runs out.</li>
     * </ol>
     *
     * @return the new order's id
     */
    public String holdForPickup(String productStoreId, String productId, String facilityId, BigDecimal quantity, String customerPartyId,
                                long holdHours, String sellingFacilityId, String userId) {
        if (holdHours <= 0) {
            throw new IllegalArgumentException("A hold lasts at least an hour");
        }
        OrderService.NewShipGroup pickupAtStore = new OrderService.NewShipGroup(PICKUP_METHOD, facilityId, null, null, NO_CARRIER, false, false,
                null, null, null, null);
        OrderService.NewItem heldProduct = new OrderService.NewItem(productId, null, quantity, BigDecimal.ZERO, null, null, 0);
        OrderService.NewOrder pickupOrder = new OrderService.NewOrder(null, productStoreId, STORE_SALES_CHANNEL, null, null, null, "Pickup hold",
                customerPartyId, null, null, List.of(pickupAtStore), List.of(heldProduct), null, null, null,
                Map.of("pickupHoldHours", String.valueOf(holdHours)), CoreStatus.Order.APPROVED, "Held for pickup", userId);
        String orderId = orderService.create(pickupOrder);

        reservationService.reserve(orderId, FIRST_ITEM, facilityId, quantity, userId);
        orderService.orderItem(orderId, FIRST_ITEM).setAutoCancelDate(CoreClock.now().plusHours(holdHours));
        if (sellingFacilityId != null) {
            setSellingFacility(orderId, sellingFacilityId);
        }
        return orderId;
    }

    // ── sales credit ──

    /** The store that sold the order (endless aisle: it may differ from the one that ships it). */
    public void setSellingFacility(String orderId, String facilityId) {
        orderService.header(orderId).setOriginFacilityId(facilityId);
    }

    /**
     * Who earns an order's sales: each item's value goes to the store that fulfils it, except that the selling store
     * keeps its share (store setting SALES_CREDIT_SELLING_PCT, 50 when unset) of items another store fulfils.
     *
     * @return value credited per facility id
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> salesCredit(String orderId) {
        OrderHeader header = orderService.header(orderId);
        String sellingFacility = header.getOriginFacilityId();
        long sellingSharePercent = header.getProductStoreId() == null
                ? DEFAULT_SELLING_SHARE_PERCENT
                : productStoreService.settingLong(header.getProductStoreId(), SELLING_SHARE_SETTING, DEFAULT_SELLING_SHARE_PERCENT);

        Map<String, BigDecimal> creditByFacility = new LinkedHashMap<>();
        for (OrderItem orderItem : orderService.items(orderId)) {
            if (CoreStatus.Item.GONE.contains(orderItem.getStatusId())) {
                continue;
            }
            BigDecimal unitPrice = orderItem.getUnitPrice() == null ? BigDecimal.ZERO : orderItem.getUnitPrice();
            BigDecimal itemValue = OrderService.openQuantity(orderItem).multiply(unitPrice);
            OrderItemShipGroup shipGroup = orderService.shipGroup(orderId, orderItem.getShipGroupSeqId());
            String fulfillingFacility = shipGroup.getFacilityId();

            boolean oneStoreDidItAll = fulfillingFacility == null || sellingFacility == null || sellingFacility.equals(fulfillingFacility);
            if (oneStoreDidItAll) {
                String creditedFacility = fulfillingFacility == null ? sellingFacility : fulfillingFacility;
                creditByFacility.merge(creditedFacility, itemValue, BigDecimal::add);
            } else {
                BigDecimal sellerShare = itemValue.multiply(BigDecimal.valueOf(sellingSharePercent)).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                creditByFacility.merge(sellingFacility, sellerShare, BigDecimal::add);
                creditByFacility.merge(fulfillingFacility, itemValue.subtract(sellerShare), BigDecimal::add);
            }
        }
        creditByFacility.remove(null);   // an order neither sold nor shipped by a known store credits no one
        return creditByFacility;
    }

    // ── rebalancing ──

    public record Suggestion(String productId, String fromFacilityId, String toFacilityId, BigDecimal quantity, double weeksOfSupplyFrom,
                             double weeksOfSupplyTo, BigDecimal unitMargin, String reason) {
    }

    /** A location's stock of one product, and how many units it can give away or still wants. */
    private static final class StockPosition {
        final ProductFacility stock;
        double units;

        StockPosition(ProductFacility stock, double units) {
            this.stock = stock;
            this.units = units;
        }
    }

    /**
     * Transfers worth making inside a store cluster, before stock has to be marked down: from locations with more than
     * {@code highWeeks} of supply (or stock that isn't selling) to locations with less than {@code lowWeeks}, bringing
     * both towards {@code targetWeeks}, and only where the product's margin beats the transfer cost per unit
     * (system property {@code nexus.core / transfer.costPerUnit}, whole currency units, 0 when unset).
     * The biggest need is filled first, from the biggest surplus first.
     */
    @Transactional(readOnly = true)
    public List<Suggestion> rebalance(String facilityGroupId, double lowWeeks, double highWeeks, double targetWeeks) {
        List<String> clusterFacilities = activeMembers(facilityGroupId);
        if (clusterFacilities.size() < 2) {
            return List.of();
        }
        BigDecimal transferCostPerUnit = BigDecimal.valueOf(systemPropertyService.getLong(RESOURCE, "transfer.costPerUnit", 0));

        Map<String, List<ProductFacility>> stockByProduct = new LinkedHashMap<>();
        entityManager.createQuery("SELECT pf FROM ProductFacility pf WHERE pf.facilityId IN :facilityIds ORDER BY pf.productId, pf.facilityId",
                        ProductFacility.class)
                .setParameter("facilityIds", clusterFacilities)
                .getResultList()
                .forEach(stock -> stockByProduct.computeIfAbsent(stock.getProductId(), productId -> new ArrayList<>()).add(stock));

        List<Suggestion> suggestions = new ArrayList<>();
        for (Map.Entry<String, List<ProductFacility>> entry : stockByProduct.entrySet()) {
            String productId = entry.getKey();
            BigDecimal unitMargin = unitMargin(productId);
            boolean worthMoving = unitMargin != null && unitMargin.compareTo(transferCostPerUnit) > 0;
            if (!worthMoving) {
                continue;
            }

            // Who has too much and who has too little, against the target weeks of supply.
            List<StockPosition> surplus = new ArrayList<>();
            List<StockPosition> shortfall = new ArrayList<>();
            for (ProductFacility stock : entry.getValue()) {
                double dailySales = dailySales(stock);
                double onShelf = onShelf(stock);
                double weeksOfSupply = weeksOfSupply(stock);
                double targetUnits = Math.ceil(dailySales * 7 * targetWeeks);
                if (weeksOfSupply > highWeeks && onShelf > targetUnits) {
                    surplus.add(new StockPosition(stock, Math.floor(onShelf - targetUnits)));
                } else if (dailySales > 0 && weeksOfSupply < lowWeeks && targetUnits > onShelf) {
                    shortfall.add(new StockPosition(stock, Math.ceil(targetUnits - onShelf)));
                }
            }
            Comparator<StockPosition> mostUnitsFirst = Comparator.comparingDouble((StockPosition position) -> position.units).reversed();
            surplus.sort(mostUnitsFirst);
            shortfall.sort(mostUnitsFirst);

            // Fill each need from the surpluses in turn.
            for (StockPosition need : shortfall) {
                for (StockPosition giver : surplus) {
                    if (need.units <= 0) {
                        break;
                    }
                    if (giver.units <= 0) {
                        continue;
                    }
                    double unitsToMove = Math.min(giver.units, need.units);
                    suggestions.add(suggestion(productId, giver.stock, need.stock, unitsToMove, unitMargin, transferCostPerUnit));
                    giver.units -= unitsToMove;
                    need.units -= unitsToMove;
                }
            }
        }
        return suggestions;
    }

    private Suggestion suggestion(String productId, ProductFacility from, ProductFacility to, double units, BigDecimal unitMargin,
                                  BigDecimal transferCostPerUnit) {
        double weeksFrom = weeksOfSupply(from);
        double weeksTo = weeksOfSupply(to);
        String fromSupply = Double.isInfinite(weeksFrom) ? "no sales and" : String.format(Locale.ROOT, "%.1f", weeksFrom);
        String reason = String.format(Locale.ROOT, "%s has %s weeks of supply, %s has %.1f; margin %s beats transfer cost %s",
                from.getFacilityId(), fromSupply, to.getFacilityId(), weeksTo, unitMargin.stripTrailingZeros().toPlainString(),
                transferCostPerUnit.toPlainString());
        return new Suggestion(productId, from.getFacilityId(), to.getFacilityId(), BigDecimal.valueOf((long) units), weeksFrom, weeksTo, unitMargin, reason);
    }

    private List<String> activeMembers(String facilityGroupId) {
        LocalDateTime now = CoreClock.now();
        return entityManager.createQuery("SELECT m.facilityId FROM FacilityGroupMember m WHERE m.facilityGroupId = :groupId AND m.fromDate <= :now "
                        + "AND (m.thruDate IS NULL OR m.thruDate > :now)", String.class)
                .setParameter("groupId", facilityGroupId)
                .setParameter("now", now)
                .getResultList();
    }

    private static double dailySales(ProductFacility stock) {
        return stock.getSalesVelocity() == null ? 0 : stock.getSalesVelocity().doubleValue();
    }

    private static double onShelf(ProductFacility stock) {
        return stock.getComputedLastInventoryCount() == null ? 0 : stock.getComputedLastInventoryCount().doubleValue();
    }

    /** Weeks the stock lasts at the current sales rate; infinite when it is not selling. */
    private static double weeksOfSupply(ProductFacility stock) {
        double dailySales = dailySales(stock);
        return dailySales > 0 ? onShelf(stock) / (dailySales * 7) : Double.POSITIVE_INFINITY;
    }

    /** Price less average cost, in the first currency the product has both in; null when either is missing. */
    private BigDecimal unitMargin(String productId) {
        LocalDateTime now = CoreClock.now();
        List<Object[]> priceRows = entityManager.createQuery(
                        "SELECT p.productPriceTypeId, p.currencyUomId, p.price FROM ProductPrice p WHERE p.productId = :productId "
                                + "AND p.productPriceTypeId IN ('DEFAULT_PRICE', 'AVERAGE_COST') AND p.fromDate <= :now "
                                + "AND (p.thruDate IS NULL OR p.thruDate > :now) ORDER BY p.fromDate DESC", Object[].class)
                .setParameter("productId", productId)
                .setParameter("now", now)
                .getResultList();

        // Newest first, so putIfAbsent keeps the current price and cost per currency.
        Map<String, BigDecimal> priceByCurrency = new LinkedHashMap<>();
        Map<String, BigDecimal> costByCurrency = new LinkedHashMap<>();
        for (Object[] row : priceRows) {
            String priceType = (String) row[0];
            String currency = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            Map<String, BigDecimal> target = "DEFAULT_PRICE".equals(priceType) ? priceByCurrency : costByCurrency;
            target.putIfAbsent(currency, amount);
        }
        for (Map.Entry<String, BigDecimal> price : priceByCurrency.entrySet()) {
            BigDecimal cost = costByCurrency.get(price.getKey());
            if (cost != null) {
                return price.getValue().subtract(cost);
            }
        }
        return null;
    }
}
