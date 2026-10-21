package com.nexus.oms.coremigration;

import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Copies one tenant's orders (nx_orders, nx_order_items) into the core order model. Run the customer, product and
 * stock copies first: orders are linked to parties, products, facilities and stores through what those copied.
 * <ul>
 *   <li>Status: pending is created, allocated is approved, shipped and delivered are completed, failed is held,
 *       cancelled is cancelled; the old status and fulfilment type are kept as order attributes.</li>
 *   <li>Money: shipping and tax become adjustments; when the old total differs from subtotal + shipping + tax, the
 *       difference becomes a discount or charge adjustment, so the grand total equals the old total.</li>
 *   <li>Ship-to: the customer's copied shipping address; an order without a customer but with an address gets a
 *       guest party holding that address.</li>
 *   <li>Stock: items of open orders allocated to a copied facility get a reservation row. No stock moves: the stock
 *       copy already took allocated stock out of available.</li>
 * </ul>
 * Safe to run again: orders already copied (NEXUS_LEGACY_ID) are skipped.
 */
@Service
public class OrderBackfill {

    private static final Logger log = LoggerFactory.getLogger(OrderBackfill.class);
    static final String LEGACY_ID = "NEXUS_LEGACY_ID";
    private static final int CHUNK = 200;
    private static final Set<String> OPEN = Set.of("ORDER_CREATED", "ORDER_APPROVED", "ORDER_HOLD");

    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final PartyService parties;
    private final TransactionTemplate tx;
    private final EntityManager em;

    public OrderBackfill(JdbcTemplate jdbc, OrderService orders, PartyService parties, TransactionTemplate tx, EntityManager em) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.parties = parties;
        this.tx = tx;
        this.em = em;
    }

    public record Result(int ordersScanned, int ordersCreated, int ordersAlreadyCopied, int itemsCreated, int itemsWithoutProduct,
                         int ordersWithoutCustomer, int guestParties, int reservations, int totalsBalanced) {
    }

    /** Lookups read once per run instead of per order. */
    private record Lookups(Set<String> copied, Map<String, String> partyByCustomer, Map<String, String> shipToByParty,
                           Map<String, String> emailByParty, Map<String, String> facilityByNode, Map<String, String> productBySku,
                           Map<String, String> itemByProductFacility, Map<String, String> storeByPlatform,
                           Map<String, Map<String, Object>> addresses, Map<String, List<Map<String, Object>>> itemsByOrder,
                           String currency) {
    }

    public Result run(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("Run the order copy as a user of the company whose orders to copy");
        Lookups l = lookups(tenantId);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, external_id, channel, channel_order_id, customer_id, status, "
                + "fulfillment_type, currency, subtotal, shipping_cost, tax_amount, total, payment_status, payment_reference, "
                + "carrier_id, tracking_number, promised_delivery, requested_ship_date, release_at, created_at, ship_to_address_id, "
                + "order_type FROM nx_orders WHERE tenant_id = ? ORDER BY created_at, id", tenantId);
        int[] t = new int[8];
        for (int from = 0; from < rows.size(); from += CHUNK) {
            List<Map<String, Object>> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK));
            int[] c = tx.execute(s -> {
                // new rows only (checked against the preloaded set), so flush once at commit
                em.setFlushMode(FlushModeType.COMMIT);
                return copy(chunk, l);
            });
            for (int i = 0; i < t.length; i++) t[i] += c[i];
        }
        log.info("Order copy for tenant {}: {} orders ({} new, {} already copied), {} items ({} without a product), "
                        + "{} without a customer ({} guest parties), {} reservations, {} totals balanced by an adjustment",
                tenantId, rows.size(), t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7]);
        return new Result(rows.size(), t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7]);
    }

    /**
     * Brings status and tracking changes made in the earlier model since {@code since} over to orders already copied.
     * The earlier model stays the system of record while the two run side by side. Returns how many orders changed.
     */
    public int refresh(UUID tenantId, LocalDateTime since) {
        Map<String, String> copied = pairs("SELECT id_value, order_id FROM order_identification WHERE order_identification_type_id = ? "
                + "AND thru_date IS NULL", LEGACY_ID);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, status, tracking_number FROM nx_orders WHERE tenant_id = ? "
                + "AND updated_at >= ?", tenantId, Timestamp.valueOf(since));
        int changed = 0;
        for (Map<String, Object> r : rows) {
            String orderId = copied.get(String.valueOf(r.get("id")));
            if (orderId == null) continue;
            String legacyStatus = str(r, "status");
            String status = orderStatus(legacyStatus);
            String tracking = str(r, "tracking_number");
            Boolean did = tx.execute(s -> {
                Map<String, Object> now = jdbc.queryForMap("SELECT h.status_id, g.tracking_number FROM order_header h LEFT JOIN "
                        + "order_item_ship_group g ON g.order_id = h.order_id AND g.ship_group_seq_id = '00001' WHERE h.order_id = ?", orderId);
                boolean statusChanged = !status.equals(now.get("status_id"));
                boolean trackingChanged = tracking != null && !tracking.equals(now.get("tracking_number"));
                if (statusChanged) {
                    orders.mirrorStatus(orderId, status, "Changed in the earlier model to " + legacyStatus);
                    orders.setAttribute(orderId, "legacyStatus", legacyStatus);
                }
                if (trackingChanged) orders.setTracking(orderId, null, tracking);
                return statusChanged || trackingChanged;
            });
            if (Boolean.TRUE.equals(did)) changed++;
        }
        return changed;
    }

    private int[] copy(List<Map<String, Object>> chunk, Lookups l) {
        int created = 0, already = 0, items = 0, noProduct = 0, noCustomer = 0, guests = 0, reservations = 0, balanced = 0;
        for (Map<String, Object> r : chunk) {
            String legacyId = String.valueOf(r.get("id"));
            if (!l.copied().add(legacyId)) {
                already++;
                continue;
            }
            String channel = upper(str(r, "channel"));
            String status = orderStatus(str(r, "status"));

            String partyId = r.get("customer_id") == null ? null : l.partyByCustomer().get(String.valueOf(r.get("customer_id")));
            String shipTo = partyId == null ? null : l.shipToByParty().get(partyId);
            Map<String, Object> address = r.get("ship_to_address_id") == null ? null : l.addresses().get(String.valueOf(r.get("ship_to_address_id")));
            if (partyId == null) {
                noCustomer++;
                if (address != null) {
                    partyId = parties.createPerson(PartyService.PersonName.parse(str(address, "full_name") == null ? "Guest" : str(address, "full_name")), null);
                    parties.addRole(partyId, "CUSTOMER");
                    shipTo = parties.addPostalAddress(partyId, addressInput(address), "SHIPPING_LOCATION");
                    guests++;
                    em.flush();   // inserts are batched by entity type at commit; the party and address must go first
                }
            } else if (address != null && shipTo == null) {
                shipTo = parties.addPostalAddress(partyId, addressInput(address), "SHIPPING_LOCATION");
                l.shipToByParty().put(partyId, shipTo);
                em.flush();
            }

            String facilityId = null;
            List<Map<String, Object>> legacyItems = l.itemsByOrder().getOrDefault(legacyId, List.of());
            List<OrderService.NewItem> newItems = new ArrayList<>();
            List<String[]> holds = new ArrayList<>();   // {itemIndex, productId, facilityId, qty}
            for (Map<String, Object> it : legacyItems) {
                String productId = l.productBySku().get(str(it, "sku"));
                if (productId == null) noProduct++;
                BigDecimal qty = num(it, "quantity");
                if (qty.signum() <= 0) continue;
                newItems.add(new OrderService.NewItem(productId, str(it, "product_name") == null ? str(it, "sku") : str(it, "product_name"),
                        qty, num(it, "unit_price"), null, str(it, "sku"), 0));
                String node = it.get("allocated_node_id") == null ? null : l.facilityByNode().get(String.valueOf(it.get("allocated_node_id")));
                if (node != null && facilityId == null) facilityId = node;
                BigDecimal allocated = num(it, "allocated_qty").subtract(num(it, "fulfilled_qty"));
                if (OPEN.contains(status) && productId != null && node != null && allocated.signum() > 0) {
                    holds.add(new String[]{String.valueOf(newItems.size() - 1), productId, node, allocated.min(qty).toPlainString()});
                }
            }
            if (newItems.isEmpty()) {
                newItems.add(new OrderService.NewItem(null, "No items in the earlier model", BigDecimal.ONE, BigDecimal.ZERO, null, null, 0));
            }
            items += newItems.size();

            BigDecimal subtotal = newItems.stream().map(i -> i.quantity().multiply(i.unitPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shipping = num(r, "shipping_cost");
            BigDecimal tax = num(r, "tax_amount");
            List<OrderService.NewAdjustment> adjustments = new ArrayList<>();
            adjustments.add(new OrderService.NewAdjustment("SHIPPING_CHARGES", shipping, null, "Shipping"));
            adjustments.add(new OrderService.NewAdjustment("SALES_TAX", tax, null, "Tax"));
            if (r.get("total") != null) {
                BigDecimal gap = num(r, "total").subtract(subtotal).subtract(shipping).subtract(tax);
                if (gap.abs().compareTo(new BigDecimal("0.005")) >= 0) {
                    adjustments.add(new OrderService.NewAdjustment(gap.signum() < 0 ? "DISCOUNT_ADJUSTMENT" : "MISCELLANEOUS_CHARGE", gap, null,
                            "Difference between the earlier total and its parts"));
                    balanced++;
                }
            }
            BigDecimal total = r.get("total") == null ? subtotal.add(shipping).add(tax) : num(r, "total");

            Map<String, String> ids = new LinkedHashMap<>();
            ids.put(LEGACY_ID, legacyId);
            if (str(r, "channel_order_id") != null) ids.put(channelIdType(channel), str(r, "channel_order_id"));
            Map<String, String> attrs = new LinkedHashMap<>();
            if (str(r, "status") != null) attrs.put("legacyStatus", str(r, "status"));
            if (str(r, "fulfillment_type") != null) attrs.put("legacyFulfillmentType", str(r, "fulfillment_type"));
            if (str(r, "carrier_id") != null) attrs.put("legacyCarrier", str(r, "carrier_id"));
            if ("B2B".equalsIgnoreCase(str(r, "order_type"))) attrs.put("legacyOrderType", "B2B");

            OrderService.NewShipGroup group = new OrderService.NewShipGroup(shipmentMethod(str(r, "fulfillment_type")), facilityId, shipTo,
                    null, null, true, false, null, null, time(r, "promised_delivery"), str(r, "tracking_number"));
            String currency = str(r, "currency") == null ? l.currency() : upper(str(r, "currency"));
            String orderId = orders.create(new OrderService.NewOrder("SALES_ORDER", l.storeByPlatform().get(channel), salesChannel(channel),
                    currency, time(r, "created_at"), str(r, "external_id"), str(r, "external_id"), partyId,
                    null, partyId == null ? null : l.emailByParty().get(partyId), List.of(group), newItems, adjustments,
                    List.of(new OrderService.NewPayment(paymentMethod(channel), total, paymentStatus(str(r, "payment_status")), str(r, "payment_reference"))),
                    ids, attrs, status, "Copied from the earlier Nexus model", null));
            for (String[] h : holds) {
                String inventoryItemId = l.itemByProductFacility().get(h[1] + "|" + h[2]);
                if (inventoryItemId == null) continue;
                orders.recordReservation(orderId, com.nexus.core.service.SequenceService.seqId(Integer.parseInt(h[0]) + 1), inventoryItemId, new BigDecimal(h[3]));
                reservations++;
            }
            created++;
        }
        return new int[]{created, already, items, noProduct, noCustomer, guests, reservations, balanced};
    }

    private Lookups lookups(UUID tenantId) {
        Set<String> copied = new HashSet<>(jdbc.queryForList("SELECT id_value FROM order_identification "
                + "WHERE order_identification_type_id = ? AND thru_date IS NULL", String.class, LEGACY_ID));
        Map<String, String> partyByCustomer = pairs("SELECT id_value, party_id FROM party_identification WHERE party_identification_type_id = ?", LEGACY_ID);
        Map<String, String> shipTo = pairs("SELECT p.party_id, p.contact_mech_id FROM party_contact_mech_purpose p WHERE p.contact_mech_purpose_type_id = ? "
                + "AND p.thru_date IS NULL ORDER BY p.from_date", "SHIPPING_LOCATION");
        Map<String, String> email = pairs("SELECT p.party_id, p.contact_mech_id FROM party_contact_mech_purpose p WHERE p.contact_mech_purpose_type_id = ? "
                + "AND p.thru_date IS NULL ORDER BY p.from_date", "PRIMARY_EMAIL");
        Map<String, String> facilityByNode = pairs("SELECT id_value, facility_id FROM facility_identification WHERE facility_iden_type_id = ? AND thru_date IS NULL", LEGACY_ID);
        Map<String, String> productBySku = pairs("SELECT id_value, product_id FROM good_identification WHERE good_identification_type_id = ? AND thru_date IS NULL", "SKU");
        Map<String, String> itemByProductFacility = new HashMap<>();
        jdbc.queryForList("SELECT product_id, facility_id, inventory_item_id FROM inventory_item ORDER BY CASE WHEN lot_id IS NULL THEN 0 ELSE 1 END, inventory_item_id")
                .forEach(x -> itemByProductFacility.putIfAbsent(x.get("product_id") + "|" + x.get("facility_id"), String.valueOf(x.get("inventory_item_id"))));
        Set<String> stores = new HashSet<>(jdbc.queryForList("SELECT product_store_id FROM product_store", String.class));
        Map<String, String> storeByPlatform = new HashMap<>();
        jdbc.queryForList("SELECT store_code, platform FROM nx_integration_stores WHERE tenant_id = ? ORDER BY created_at", tenantId).forEach(x -> {
            String id = ProductBackfill.storeId(str(x, "store_code"), str(x, "platform"));
            if (stores.contains(id) && str(x, "platform") != null) storeByPlatform.putIfAbsent(upper(str(x, "platform")), id);
        });
        Map<String, Map<String, Object>> addresses = new HashMap<>();
        jdbc.queryForList("SELECT id, full_name, address_line1, address_line2, city, state, postal_code, country FROM nx_addresses "
                + "WHERE tenant_id = ?", tenantId).forEach(a -> addresses.put(String.valueOf(a.get("id")), a));
        Map<String, List<Map<String, Object>>> itemsByOrder = new HashMap<>();
        jdbc.queryForList("SELECT i.order_id, i.sku, i.product_name, i.quantity, i.unit_price, i.allocated_node_id, i.allocated_qty, i.fulfilled_qty "
                        + "FROM nx_order_items i JOIN nx_orders o ON o.id = i.order_id WHERE o.tenant_id = ? ORDER BY i.order_id, i.id", tenantId)
                .forEach(i -> itemsByOrder.computeIfAbsent(String.valueOf(i.get("order_id")), k -> new ArrayList<>()).add(i));
        List<String> cur = jdbc.queryForList("SELECT default_currency FROM nx_company_settings WHERE tenant_id = ? AND default_currency IS NOT NULL", String.class, tenantId);
        return new Lookups(copied, partyByCustomer, shipTo, email, facilityByNode, productBySku, itemByProductFacility, storeByPlatform,
                addresses, itemsByOrder, cur.isEmpty() ? null : upper(cur.get(0)));
    }

    /** First value per key, in query order. */
    private Map<String, String> pairs(String sql, Object arg) {
        Map<String, String> m = new HashMap<>();
        jdbc.query(sql, rs -> {
            m.putIfAbsent(rs.getString(1), rs.getString(2));
        }, arg);
        return m;
    }

    static String orderStatus(String legacy) {
        if (legacy == null) return "ORDER_CREATED";
        return switch (upper(legacy)) {
            case "ALLOCATED", "PARTIALLY_ALLOCATED", "RELEASED", "PICKING", "PACKED" -> "ORDER_APPROVED";
            case "SHIPPED", "DELIVERED", "COMPLETED", "FULFILLED" -> "ORDER_COMPLETED";
            case "CANCELLED", "CANCELED" -> "ORDER_CANCELLED";
            case "FAILED", "ON_HOLD", "HOLD" -> "ORDER_HOLD";
            case "REJECTED" -> "ORDER_REJECTED";
            default -> "ORDER_CREATED";
        };
    }

    static String salesChannel(String channel) {
        if (channel == null) return "UNKNWN_SALES_CHANNEL";
        return switch (channel) {
            case "SHOPIFY", "BIGCOMMERCE", "WOOCOMMERCE", "MAGENTO", "WEB", "SALESFORCE_COMMERCE" -> "WEB_SALES_CHANNEL";
            case "AMAZON", "EBAY", "WALMART", "FLIPKART" -> "MARKETPLACE_SALES_CHANNEL";
            case "POS", "SHOPIFY_POS" -> "POS_SALES_CHANNEL";
            case "B2B_PORTAL", "EDI", "B2B" -> "B2B_SALES_CHANNEL";
            case "PHONE" -> "PHONE_SALES_CHANNEL";
            default -> "UNKNWN_SALES_CHANNEL";
        };
    }

    static String channelIdType(String channel) {
        if (channel == null) return "CHANNEL_ORD_ID";
        return switch (channel) {
            case "SHOPIFY" -> "SHOPIFY_ORD_ID";
            case "BIGCOMMERCE" -> "BIGCOMMERCE_ORD_ID";
            case "WOOCOMMERCE" -> "WOOCOMMERCE_ORD_ID";
            case "AMAZON" -> "AMAZON_ORD_ID";
            case "EBAY", "WALMART", "FLIPKART" -> "MARKETPLACE_ORD_ID";
            case "POS", "SHOPIFY_POS" -> "POS_ORD_ID";
            case "EDI", "B2B_PORTAL" -> "EDI_PO_NUMBER";
            default -> "CHANNEL_ORD_ID";
        };
    }

    static String paymentMethod(String channel) {
        if (channel == null) return "EXT_OFFLINE";
        return switch (channel) {
            case "SHOPIFY", "SHOPIFY_POS" -> "EXT_SHOPIFY";
            case "BIGCOMMERCE" -> "EXT_BIGCOMMERCE";
            case "AMAZON", "EBAY", "WALMART", "FLIPKART" -> "EXT_MARKETPLACE";
            case "B2B_PORTAL", "EDI" -> "EXT_BILLING_ACCOUNT";
            default -> "EXT_OFFLINE";
        };
    }

    static String paymentStatus(String legacy) {
        if (legacy == null) return "PAYMENT_NOT_RECEIVED";
        return switch (upper(legacy)) {
            case "PAID", "CAPTURED", "SETTLED" -> "PAYMENT_SETTLED";
            case "AUTHORIZED" -> "PAYMENT_AUTHORIZED";
            case "REFUNDED" -> "PAYMENT_REFUNDED";
            case "DECLINED", "FAILED" -> "PAYMENT_DECLINED";
            default -> "PAYMENT_NOT_RECEIVED";
        };
    }

    static String shipmentMethod(String fulfillmentType) {
        if (fulfillmentType == null) return "STANDARD";
        return switch (upper(fulfillmentType)) {
            case "BOPIS", "PICKUP", "STORE_PICKUP", "CURBSIDE" -> "STOREPICKUP";
            case "EXPRESS" -> "EXPRESS";
            case "NEXT_DAY" -> "NEXT_DAY";
            case "SAME_DAY" -> "SAME_DAY";
            default -> "STANDARD";
        };
    }

    private static PartyService.AddressInput addressInput(Map<String, Object> a) {
        return new PartyService.AddressInput(str(a, "full_name"), str(a, "address_line1"), str(a, "address_line2"), str(a, "city"),
                str(a, "state"), str(a, "postal_code"), str(a, "country"), null, null, null);
    }

    private static LocalDateTime time(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v instanceof Timestamp t ? t.toLocalDateTime() : v instanceof LocalDateTime d ? d : null;
    }

    private static BigDecimal num(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String str(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }
}
