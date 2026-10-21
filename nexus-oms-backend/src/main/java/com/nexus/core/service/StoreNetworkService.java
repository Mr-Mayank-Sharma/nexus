package com.nexus.core.service;

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
import java.util.Map;
import java.util.Set;

/**
 * How stores work with each other (doc 24, section 4b). Stores never talk directly: every lookup, hold, credit and
 * suggestion goes through the one stock record per store the OMS keeps.
 */
@Service("coreStoreNetworkService")
@Transactional
public class StoreNetworkService {

    static final String RESOURCE = "nexus.core";
    static final Set<String> VIRTUAL = Set.of("VIRTUAL_FACILITY", "IN_TRANSIT", "DROPSHIP");

    private final EntityManager em;
    private final OrderService orders;
    private final ProductStoreService stores;
    private final SystemPropertyService properties;

    public StoreNetworkService(EntityManager em, OrderService orders, ProductStoreService stores, SystemPropertyService properties) {
        this.em = em;
        this.orders = orders;
        this.stores = stores;
        this.properties = properties;
    }

    // ── lookup with confidence ──

    public enum Confidence { HIGH, MEDIUM, LOW }

    public record Availability(String facilityId, String facilityName, BigDecimal sellable, Confidence confidence, List<String> reasons) {
    }

    /**
     * Which locations can sell a product now, most confident and most stock first. Confidence comes from how many are
     * left, when the stock was last counted, and whether the location recently failed to find this product.
     */
    @Transactional(readOnly = true)
    public List<Availability> lookup(String productId) {
        LocalDateTime now = CoreClock.now();
        List<Availability> out = new ArrayList<>();
        for (ProductFacility pf : em.createQuery("SELECT pf FROM ProductFacility pf WHERE pf.productId = :p AND pf.computedLastInventoryCount > 0",
                ProductFacility.class).setParameter("p", productId).getResultList()) {
            Facility f = em.find(Facility.class, pf.getFacilityId());
            if (f == null || VIRTUAL.contains(f.getFacilityTypeId())) continue;
            List<String> reasons = new ArrayList<>();
            int score = 0;
            BigDecimal q = pf.getComputedLastInventoryCount();
            if (q.compareTo(BigDecimal.valueOf(3)) >= 0) score++;
            else if (q.compareTo(BigDecimal.ONE) <= 0) { score -= 2; reasons.add("only one left"); }
            else reasons.add("only " + q.stripTrailingZeros().toPlainString() + " left");
            // from the counts themselves: a count that matched the shelf changes no stock but still counts
            LocalDateTime counted = em.createQuery("SELECT MAX(c.physicalInventoryDate) FROM InventoryItemVariance v, PhysicalInventory c, InventoryItem i "
                    + "WHERE c.physicalInventoryId = v.physicalInventoryId AND i.inventoryItemId = v.inventoryItemId AND i.productId = :p AND i.facilityId = :f", LocalDateTime.class)
                    .setParameter("p", productId).setParameter("f", pf.getFacilityId()).getSingleResult();
            if (counted == null) { score--; reasons.add("never counted"); }
            else {
                long days = Duration.between(counted, now).toDays();
                if (days <= 30) { score++; reasons.add("counted " + days + " day(s) ago"); }
                else if (days > 90) { score--; reasons.add("last counted " + days + " days ago"); }
            }
            long misses = em.createQuery("SELECT COUNT(x) FROM ExcludedOrderFacility x, OrderItem i WHERE i.orderId = x.orderId "
                    + "AND i.orderItemSeqId = x.orderItemSeqId AND x.facilityId = :f AND i.productId = :p AND x.fromDate >= :since", Long.class)
                    .setParameter("f", pf.getFacilityId()).setParameter("p", productId).setParameter("since", now.minusDays(30)).getSingleResult();
            if (misses > 0) { score -= 2; reasons.add("could not find it " + misses + " time(s) in 30 days"); }
            Confidence c = score >= 2 ? Confidence.HIGH : score <= -1 ? Confidence.LOW : Confidence.MEDIUM;
            out.add(new Availability(pf.getFacilityId(), f.getFacilityName(), q, c, reasons));
        }
        out.sort(Comparator.comparing(Availability::confidence).thenComparing(Availability::sellable, Comparator.reverseOrder())
                .thenComparing(Availability::facilityId));
        return out;
    }

    // ── hold at another store for pickup ──

    /**
     * Holds a product at a store for a customer to collect: an approved pickup order at that store, the stock held at
     * once, the item set to cancel when the hold runs out. Returns the order id.
     */
    public String holdForPickup(String productStoreId, String productId, String facilityId, BigDecimal quantity, String customerPartyId,
                                long holdHours, String sellingFacilityId, String userId) {
        if (holdHours <= 0) throw new IllegalArgumentException("A hold lasts at least an hour");
        String orderId = orders.create(new OrderService.NewOrder(null, productStoreId, "POS_SALES_CHANNEL", null, null, null, "Pickup hold", customerPartyId,
                null, null, List.of(new OrderService.NewShipGroup("STOREPICKUP", facilityId, null, null, "_NA_", false, false, null, null, null, null)),
                List.of(new OrderService.NewItem(productId, null, quantity, BigDecimal.ZERO, null, null, 0)), null, null, null,
                Map.of("pickupHoldHours", String.valueOf(holdHours)), "ORDER_APPROVED", "Held for pickup", userId));
        orders.reserve(orderId, "00001", facilityId, quantity, userId);
        orders.orderItem(orderId, "00001").setAutoCancelDate(CoreClock.now().plusHours(holdHours));
        if (sellingFacilityId != null) setSellingFacility(orderId, sellingFacilityId);
        return orderId;
    }

    // ── sales credit ──

    /** The store that sold the order (endless aisle: it may differ from the one that ships it). */
    public void setSellingFacility(String orderId, String facilityId) {
        OrderHeader h = em.find(OrderHeader.class, orderId);
        if (h == null) throw new IllegalArgumentException("Unknown order " + orderId);
        h.setOriginFacilityId(facilityId);
    }

    /**
     * Who earns an order's sales: each item's value goes to the store that fulfils it, except that the selling store
     * keeps its share (store setting SALES_CREDIT_SELLING_PCT, 50 when unset) of items another store fulfils.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> salesCredit(String orderId) {
        OrderHeader h = em.find(OrderHeader.class, orderId);
        if (h == null) throw new IllegalArgumentException("Unknown order " + orderId);
        String seller = h.getOriginFacilityId();
        BigDecimal pct = BigDecimal.valueOf(h.getProductStoreId() == null ? 50 : stores.settingLong(h.getProductStoreId(), "SALES_CREDIT_SELLING_PCT", 50));
        Map<String, BigDecimal> credit = new LinkedHashMap<>();
        for (OrderItem i : orders.items(orderId)) {
            if ("ITEM_CANCELLED".equals(i.getStatusId()) || "ITEM_REJECTED".equals(i.getStatusId())) continue;
            BigDecimal value = OrderService.openQuantity(i).multiply(i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice());
            OrderItemShipGroup g = orders.shipGroup(orderId, i.getShipGroupSeqId());
            String fulfiller = g.getFacilityId();
            if (fulfiller == null || seller == null || seller.equals(fulfiller)) {
                credit.merge(fulfiller == null ? seller : fulfiller, value, BigDecimal::add);
            } else {
                BigDecimal sellerShare = value.multiply(pct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                credit.merge(seller, sellerShare, BigDecimal::add);
                credit.merge(fulfiller, value.subtract(sellerShare), BigDecimal::add);
            }
        }
        credit.remove(null);
        return credit;
    }

    // ── rebalancing ──

    public record Suggestion(String productId, String fromFacilityId, String toFacilityId, BigDecimal quantity, double weeksOfSupplyFrom,
                             double weeksOfSupplyTo, BigDecimal unitMargin, String reason) {
    }

    /**
     * Transfers worth making inside a store cluster, before stock has to be marked down: from locations with more than
     * {@code highWeeks} of supply (or stock that isn't selling) to locations with less than {@code lowWeeks}, bringing
     * both towards {@code targetWeeks}, and only where the product's margin beats the transfer cost per unit
     * (system property {@code nexus.core / transfer.costPerUnit}, whole currency units, 0 when unset).
     */
    @Transactional(readOnly = true)
    public List<Suggestion> rebalance(String facilityGroupId, double lowWeeks, double highWeeks, double targetWeeks) {
        LocalDateTime now = CoreClock.now();
        List<String> members = em.createQuery("SELECT m.facilityId FROM FacilityGroupMember m WHERE m.facilityGroupId = :g AND m.fromDate <= :now "
                + "AND (m.thruDate IS NULL OR m.thruDate > :now)", String.class).setParameter("g", facilityGroupId).setParameter("now", now).getResultList();
        if (members.size() < 2) return List.of();
        BigDecimal transferCost = BigDecimal.valueOf(properties.getLong(RESOURCE, "transfer.costPerUnit", 0));
        Map<String, List<ProductFacility>> byProduct = new LinkedHashMap<>();
        em.createQuery("SELECT pf FROM ProductFacility pf WHERE pf.facilityId IN :f ORDER BY pf.productId, pf.facilityId", ProductFacility.class)
                .setParameter("f", members).getResultList().forEach(pf -> byProduct.computeIfAbsent(pf.getProductId(), k -> new ArrayList<>()).add(pf));
        List<Suggestion> out = new ArrayList<>();
        for (Map.Entry<String, List<ProductFacility>> e : byProduct.entrySet()) {
            BigDecimal margin = margin(e.getKey());
            if (margin == null || margin.compareTo(transferCost) <= 0) continue;
            List<double[]> give = new ArrayList<>();   // {index, units}
            List<double[]> want = new ArrayList<>();
            List<ProductFacility> rows = e.getValue();
            for (int i = 0; i < rows.size(); i++) {
                ProductFacility pf = rows.get(i);
                double v = pf.getSalesVelocity() == null ? 0 : pf.getSalesVelocity().doubleValue();
                double have = pf.getComputedLastInventoryCount() == null ? 0 : pf.getComputedLastInventoryCount().doubleValue();
                double weeks = v > 0 ? have / (v * 7) : Double.POSITIVE_INFINITY;
                double target = Math.ceil(v * 7 * targetWeeks);
                if (weeks > highWeeks && have > target) give.add(new double[]{i, Math.floor(have - target)});
                else if (v > 0 && weeks < lowWeeks && target > have) want.add(new double[]{i, Math.ceil(target - have)});
            }
            give.sort(Comparator.comparingDouble((double[] x) -> x[1]).reversed());
            want.sort(Comparator.comparingDouble((double[] x) -> x[1]).reversed());
            for (double[] w : want) {
                for (double[] g : give) {
                    if (w[1] <= 0) break;
                    if (g[1] <= 0) continue;
                    double q = Math.min(g[1], w[1]);
                    ProductFacility from = rows.get((int) g[0]), to = rows.get((int) w[0]);
                    out.add(new Suggestion(e.getKey(), from.getFacilityId(), to.getFacilityId(), BigDecimal.valueOf((long) q),
                            weeks(from), weeks(to), margin, String.format(java.util.Locale.ROOT, "%s has %s weeks of supply, %s has %.1f; margin %s beats transfer cost %s",
                            from.getFacilityId(), Double.isInfinite(weeks(from)) ? "no sales and" : String.format(java.util.Locale.ROOT, "%.1f", weeks(from)),
                            to.getFacilityId(), weeks(to), margin.stripTrailingZeros().toPlainString(), transferCost.toPlainString())));
                    g[1] -= q;
                    w[1] -= q;
                }
            }
        }
        return out;
    }

    private static double weeks(ProductFacility pf) {
        double v = pf.getSalesVelocity() == null ? 0 : pf.getSalesVelocity().doubleValue();
        double have = pf.getComputedLastInventoryCount() == null ? 0 : pf.getComputedLastInventoryCount().doubleValue();
        return v > 0 ? have / (v * 7) : Double.POSITIVE_INFINITY;
    }

    /** Price less average cost, in the first currency the product has both in; null when either is missing. */
    private BigDecimal margin(String productId) {
        LocalDateTime now = CoreClock.now();
        List<Object[]> prices = em.createQuery("SELECT p.productPriceTypeId, p.currencyUomId, p.price FROM ProductPrice p WHERE p.productId = :p "
                + "AND p.productPriceTypeId IN ('DEFAULT_PRICE', 'AVERAGE_COST') AND p.fromDate <= :now AND (p.thruDate IS NULL OR p.thruDate > :now) "
                + "ORDER BY p.fromDate DESC", Object[].class).setParameter("p", productId).setParameter("now", now).getResultList();
        Map<String, BigDecimal> price = new LinkedHashMap<>(), cost = new LinkedHashMap<>();
        for (Object[] r : prices) {
            (("DEFAULT_PRICE".equals(r[0])) ? price : cost).putIfAbsent((String) r[1], (BigDecimal) r[2]);
        }
        for (Map.Entry<String, BigDecimal> p : price.entrySet()) {
            BigDecimal c = cost.get(p.getKey());
            if (c != null) return p.getValue().subtract(c);
        }
        return null;
    }
}
