package com.nexus.oms.coremigration;

import com.nexus.core.CoreStatus;
import com.nexus.core.service.OrderReservationService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.SequenceService;
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
 * Safe to run again: orders already copied (NEXUS_LEGACY_ID) are skipped. Orders are copied in chunks of
 * {@value #CHUNK_SIZE}, one transaction per chunk.
 */
@Service
public class OrderBackfill {

    private static final Logger log = LoggerFactory.getLogger(OrderBackfill.class);

    static final String LEGACY_ID = "NEXUS_LEGACY_ID";
    static final String SHIPPING_PURPOSE = "SHIPPING_LOCATION";
    static final String EMAIL_PURPOSE = "PRIMARY_EMAIL";
    private static final int CHUNK_SIZE = 200;
    /** A gap between the old total and its parts smaller than this is rounding, not a discount. */
    private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.005");

    private final JdbcTemplate jdbc;
    private final OrderService orderService;
    private final OrderReservationService reservationService;
    private final PartyService partyService;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    public OrderBackfill(JdbcTemplate jdbc, OrderService orderService, OrderReservationService reservationService, PartyService partyService,
                         TransactionTemplate transaction, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.partyService = partyService;
        this.transaction = transaction;
        this.entityManager = entityManager;
    }

    public record Result(int ordersScanned, int ordersCreated, int ordersAlreadyCopied, int itemsCreated, int itemsWithoutProduct,
                         int ordersWithoutCustomer, int guestParties, int reservations, int totalsBalanced) {
    }

    /** Running totals of one copy, added up over the chunks. */
    private static final class Counts {
        int ordersCreated;
        int ordersAlreadyCopied;
        int itemsCreated;
        int itemsWithoutProduct;
        int ordersWithoutCustomer;
        int guestParties;
        int reservations;
        int totalsBalanced;

        void add(Counts other) {
            ordersCreated += other.ordersCreated;
            ordersAlreadyCopied += other.ordersAlreadyCopied;
            itemsCreated += other.itemsCreated;
            itemsWithoutProduct += other.itemsWithoutProduct;
            ordersWithoutCustomer += other.ordersWithoutCustomer;
            guestParties += other.guestParties;
            reservations += other.reservations;
            totalsBalanced += other.totalsBalanced;
        }
    }

    /** Lookups read once per run instead of once per order. */
    private record Lookups(Set<String> copiedLegacyIds, Map<String, String> partyByCustomer, Map<String, String> shipToByParty,
                           Map<String, String> emailByParty, Map<String, String> facilityByNode, Map<String, String> productBySku,
                           Map<String, String> inventoryItemByProductFacility, Map<String, String> storeByPlatform,
                           Map<String, Map<String, Object>> addressById, Map<String, List<Map<String, Object>>> itemsByOrder,
                           String defaultCurrency) {
    }

    /** Stock an old item had allocated, to be recorded as a hold of the new item ({@code itemIndex} counts from 0). */
    private record Hold(int itemIndex, String productId, String facilityId, BigDecimal quantity) {
    }

    // ── full copy ──

    public Result run(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Run the order copy as a user of the company whose orders to copy");
        }
        Lookups lookups = loadLookups(tenantId);
        List<Map<String, Object>> legacyOrders = jdbc.queryForList("SELECT id, external_id, channel, channel_order_id, customer_id, status, "
                + "fulfillment_type, currency, subtotal, shipping_cost, tax_amount, total, payment_status, payment_reference, "
                + "carrier_id, tracking_number, promised_delivery, requested_ship_date, release_at, created_at, ship_to_address_id, "
                + "order_type FROM nx_orders WHERE tenant_id = ? ORDER BY created_at, id", tenantId);

        Counts total = new Counts();
        for (int start = 0; start < legacyOrders.size(); start += CHUNK_SIZE) {
            List<Map<String, Object>> chunk = legacyOrders.subList(start, Math.min(legacyOrders.size(), start + CHUNK_SIZE));
            Counts chunkCounts = transaction.execute(status -> {
                // Only new rows (checked against the preloaded set), so one flush at commit is enough.
                entityManager.setFlushMode(FlushModeType.COMMIT);
                return copyChunk(chunk, lookups);
            });
            total.add(chunkCounts);
        }
        log.info("Order copy for tenant {}: {} orders ({} new, {} already copied), {} items ({} without a product), "
                        + "{} without a customer ({} guest parties), {} reservations, {} totals balanced by an adjustment",
                tenantId, legacyOrders.size(), total.ordersCreated, total.ordersAlreadyCopied, total.itemsCreated, total.itemsWithoutProduct,
                total.ordersWithoutCustomer, total.guestParties, total.reservations, total.totalsBalanced);
        return new Result(legacyOrders.size(), total.ordersCreated, total.ordersAlreadyCopied, total.itemsCreated, total.itemsWithoutProduct,
                total.ordersWithoutCustomer, total.guestParties, total.reservations, total.totalsBalanced);
    }

    private Counts copyChunk(List<Map<String, Object>> chunk, Lookups lookups) {
        Counts counts = new Counts();
        for (Map<String, Object> legacyOrder : chunk) {
            String legacyId = String.valueOf(legacyOrder.get("id"));
            boolean firstTimeSeen = lookups.copiedLegacyIds().add(legacyId);
            if (!firstTimeSeen) {
                counts.ordersAlreadyCopied++;
                continue;
            }
            copyOrder(legacyId, legacyOrder, lookups, counts);
        }
        return counts;
    }

    /** Copies one old order: customer and ship-to, items, money, ids, the order itself, then its holds. */
    private void copyOrder(String legacyId, Map<String, Object> legacyOrder, Lookups lookups, Counts counts) {
        String channel = upper(text(legacyOrder, "channel"));
        String orderStatus = orderStatus(text(legacyOrder, "status"));

        // Step 1: who placed it and where it goes.
        CustomerAndShipTo customer = customerAndShipTo(legacyOrder, lookups, counts);
        String partyId = customer.partyId();
        String shipToContactMechId = customer.shipToContactMechId();

        // Step 2: items, and the stock open orders had allocated.
        List<OrderService.NewItem> newItems = new ArrayList<>();
        List<Hold> holds = new ArrayList<>();
        String facilityId = null;
        for (Map<String, Object> legacyItem : lookups.itemsByOrder().getOrDefault(legacyId, List.of())) {
            String sku = text(legacyItem, "sku");
            String productId = lookups.productBySku().get(sku);
            if (productId == null) {
                counts.itemsWithoutProduct++;
            }
            BigDecimal quantity = number(legacyItem, "quantity");
            if (quantity.signum() <= 0) {
                continue;
            }
            String description = text(legacyItem, "product_name") == null ? sku : text(legacyItem, "product_name");
            newItems.add(new OrderService.NewItem(productId, description, quantity, number(legacyItem, "unit_price"), null, sku, 0));

            Object nodeId = legacyItem.get("allocated_node_id");
            String allocatedFacility = nodeId == null ? null : lookups.facilityByNode().get(String.valueOf(nodeId));
            if (allocatedFacility != null && facilityId == null) {
                facilityId = allocatedFacility;   // the order ships from where its first allocated item is
            }
            BigDecimal stillAllocated = number(legacyItem, "allocated_qty").subtract(number(legacyItem, "fulfilled_qty"));
            boolean holdsStock = CoreStatus.Order.OPEN.contains(orderStatus) && productId != null && allocatedFacility != null && stillAllocated.signum() > 0;
            if (holdsStock) {
                holds.add(new Hold(newItems.size() - 1, productId, allocatedFacility, stillAllocated.min(quantity)));
            }
        }
        if (newItems.isEmpty()) {
            newItems.add(new OrderService.NewItem(null, "No items in the earlier model", BigDecimal.ONE, BigDecimal.ZERO, null, null, 0));
        }
        counts.itemsCreated += newItems.size();

        // Step 3: money. Shipping and tax are adjustments; any gap to the old total is one more.
        BigDecimal subtotal = newItems.stream().map(item -> item.quantity().multiply(item.unitPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = number(legacyOrder, "shipping_cost");
        BigDecimal tax = number(legacyOrder, "tax_amount");
        List<OrderService.NewAdjustment> adjustments = new ArrayList<>();
        adjustments.add(new OrderService.NewAdjustment("SHIPPING_CHARGES", shipping, null, "Shipping"));
        adjustments.add(new OrderService.NewAdjustment("SALES_TAX", tax, null, "Tax"));
        boolean hasOldTotal = legacyOrder.get("total") != null;
        if (hasOldTotal) {
            BigDecimal gap = number(legacyOrder, "total").subtract(subtotal).subtract(shipping).subtract(tax);
            if (gap.abs().compareTo(ROUNDING_TOLERANCE) >= 0) {
                String adjustmentType = gap.signum() < 0 ? "DISCOUNT_ADJUSTMENT" : "MISCELLANEOUS_CHARGE";
                adjustments.add(new OrderService.NewAdjustment(adjustmentType, gap, null, "Difference between the earlier total and its parts"));
                counts.totalsBalanced++;
            }
        }
        BigDecimal grandTotal = hasOldTotal ? number(legacyOrder, "total") : subtotal.add(shipping).add(tax);

        // Step 4: ids and attributes that keep the link to the earlier model.
        Map<String, String> identifications = new LinkedHashMap<>();
        identifications.put(LEGACY_ID, legacyId);
        if (text(legacyOrder, "channel_order_id") != null) {
            identifications.put(channelIdType(channel), text(legacyOrder, "channel_order_id"));
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "legacyStatus", text(legacyOrder, "status"));
        putIfPresent(attributes, "legacyFulfillmentType", text(legacyOrder, "fulfillment_type"));
        putIfPresent(attributes, "legacyCarrier", text(legacyOrder, "carrier_id"));
        if ("B2B".equalsIgnoreCase(text(legacyOrder, "order_type"))) {
            attributes.put("legacyOrderType", "B2B");
        }

        // Step 5: the order.
        OrderService.NewShipGroup shipGroup = new OrderService.NewShipGroup(shipmentMethod(text(legacyOrder, "fulfillment_type")), facilityId,
                shipToContactMechId, null, null, true, false, null, null, time(legacyOrder, "promised_delivery"), text(legacyOrder, "tracking_number"));
        String currency = text(legacyOrder, "currency") == null ? lookups.defaultCurrency() : upper(text(legacyOrder, "currency"));
        OrderService.NewPayment payment = new OrderService.NewPayment(paymentMethod(channel), grandTotal, paymentStatus(text(legacyOrder, "payment_status")),
                text(legacyOrder, "payment_reference"));
        String emailContactMechId = partyId == null ? null : lookups.emailByParty().get(partyId);
        OrderService.NewOrder newOrder = new OrderService.NewOrder("SALES_ORDER", lookups.storeByPlatform().get(channel), salesChannel(channel),
                currency, time(legacyOrder, "created_at"), text(legacyOrder, "external_id"), text(legacyOrder, "external_id"), partyId,
                null, emailContactMechId, List.of(shipGroup), newItems, adjustments, List.of(payment),
                identifications, attributes, orderStatus, "Copied from the earlier Nexus model", null);
        String orderId = orderService.create(newOrder);

        // Step 6: holds. The stock copy already took this stock out of available, so only the hold rows are written.
        for (Hold hold : holds) {
            String inventoryItemId = lookups.inventoryItemByProductFacility().get(hold.productId() + "|" + hold.facilityId());
            if (inventoryItemId == null) {
                continue;
            }
            String orderItemSeqId = SequenceService.seqId(hold.itemIndex() + 1);
            reservationService.recordReservation(orderId, orderItemSeqId, inventoryItemId, hold.quantity());
            counts.reservations++;
        }
        counts.ordersCreated++;
    }

    /** Either may be null: an order can have no known customer and no address. */
    private record CustomerAndShipTo(String partyId, String shipToContactMechId) {
    }

    /**
     * The order's party and ship-to address. An order
     * without a copied customer but with an address gets a guest party for it; a customer without a copied shipping
     * address gets the order's address.
     */
    private CustomerAndShipTo customerAndShipTo(Map<String, Object> legacyOrder, Lookups lookups, Counts counts) {
        Object customerId = legacyOrder.get("customer_id");
        String partyId = customerId == null ? null : lookups.partyByCustomer().get(String.valueOf(customerId));
        String shipTo = partyId == null ? null : lookups.shipToByParty().get(partyId);
        Object addressId = legacyOrder.get("ship_to_address_id");
        Map<String, Object> address = addressId == null ? null : lookups.addressById().get(String.valueOf(addressId));

        if (partyId == null) {
            counts.ordersWithoutCustomer++;
            if (address != null) {
                String fullName = text(address, "full_name") == null ? "Guest" : text(address, "full_name");
                partyId = partyService.createPerson(PartyService.PersonName.parse(fullName), null);
                partyService.addRole(partyId, "CUSTOMER");
                shipTo = partyService.addPostalAddress(partyId, addressInput(address), SHIPPING_PURPOSE);
                counts.guestParties++;
                entityManager.flush();   // inserts are batched by entity type at commit; the party and address must go first
            }
        } else if (address != null && shipTo == null) {
            shipTo = partyService.addPostalAddress(partyId, addressInput(address), SHIPPING_PURPOSE);
            lookups.shipToByParty().put(partyId, shipTo);
            entityManager.flush();
        }
        return new CustomerAndShipTo(partyId, shipTo);
    }

    // ── keeping copies in step ──

    /**
     * Brings status and tracking changes made in the earlier model since {@code since} over to orders already copied.
     * The earlier model stays the system of record while the two run side by side. Returns how many orders changed.
     */
    public int refresh(UUID tenantId, LocalDateTime since) {
        Map<String, String> orderIdByLegacyId = firstValuePerKey("SELECT id_value, order_id FROM order_identification "
                + "WHERE order_identification_type_id = ? AND thru_date IS NULL", LEGACY_ID);
        List<Map<String, Object>> changedLegacyOrders = jdbc.queryForList("SELECT id, status, tracking_number FROM nx_orders WHERE tenant_id = ? "
                + "AND updated_at >= ?", tenantId, Timestamp.valueOf(since));

        int ordersChanged = 0;
        for (Map<String, Object> legacyOrder : changedLegacyOrders) {
            String orderId = orderIdByLegacyId.get(String.valueOf(legacyOrder.get("id")));
            if (orderId == null) {
                continue;   // not copied yet: the next full copy picks it up
            }
            String legacyStatus = text(legacyOrder, "status");
            String newStatus = orderStatus(legacyStatus);
            String newTracking = text(legacyOrder, "tracking_number");
            Boolean changed = transaction.execute(status -> {
                Map<String, Object> current = jdbc.queryForMap("SELECT h.status_id, g.tracking_number FROM order_header h LEFT JOIN "
                        + "order_item_ship_group g ON g.order_id = h.order_id AND g.ship_group_seq_id = '00001' WHERE h.order_id = ?", orderId);
                boolean statusChanged = !newStatus.equals(current.get("status_id"));
                boolean trackingChanged = newTracking != null && !newTracking.equals(current.get("tracking_number"));
                if (statusChanged) {
                    orderService.mirrorStatus(orderId, newStatus, "Changed in the earlier model to " + legacyStatus);
                    orderService.setAttribute(orderId, "legacyStatus", legacyStatus);
                }
                if (trackingChanged) {
                    orderService.setTracking(orderId, null, newTracking);
                }
                return statusChanged || trackingChanged;
            });
            if (Boolean.TRUE.equals(changed)) {
                ordersChanged++;
            }
        }
        return ordersChanged;
    }

    // ── lookups ──

    private Lookups loadLookups(UUID tenantId) {
        Set<String> copiedLegacyIds = new HashSet<>(jdbc.queryForList("SELECT id_value FROM order_identification "
                + "WHERE order_identification_type_id = ? AND thru_date IS NULL", String.class, LEGACY_ID));
        Map<String, String> partyByCustomer = firstValuePerKey(
                "SELECT id_value, party_id FROM party_identification WHERE party_identification_type_id = ?", LEGACY_ID);
        Map<String, String> shipToByParty = firstValuePerKey("SELECT p.party_id, p.contact_mech_id FROM party_contact_mech_purpose p "
                + "WHERE p.contact_mech_purpose_type_id = ? AND p.thru_date IS NULL ORDER BY p.from_date", SHIPPING_PURPOSE);
        Map<String, String> emailByParty = firstValuePerKey("SELECT p.party_id, p.contact_mech_id FROM party_contact_mech_purpose p "
                + "WHERE p.contact_mech_purpose_type_id = ? AND p.thru_date IS NULL ORDER BY p.from_date", EMAIL_PURPOSE);
        Map<String, String> facilityByNode = firstValuePerKey(
                "SELECT id_value, facility_id FROM facility_identification WHERE facility_iden_type_id = ? AND thru_date IS NULL", LEGACY_ID);
        Map<String, String> productBySku = firstValuePerKey(
                "SELECT id_value, product_id FROM good_identification WHERE good_identification_type_id = ? AND thru_date IS NULL", "SKU");

        // The bulk (no lot) inventory item of each product at each facility comes first, so it is the one picked.
        Map<String, String> inventoryItemByProductFacility = new HashMap<>();
        jdbc.queryForList("SELECT product_id, facility_id, inventory_item_id FROM inventory_item "
                        + "ORDER BY CASE WHEN lot_id IS NULL THEN 0 ELSE 1 END, inventory_item_id")
                .forEach(row -> inventoryItemByProductFacility.putIfAbsent(row.get("product_id") + "|" + row.get("facility_id"),
                        String.valueOf(row.get("inventory_item_id"))));

        // The first copied store of each platform takes that platform's orders.
        Set<String> copiedStores = new HashSet<>(jdbc.queryForList("SELECT product_store_id FROM product_store", String.class));
        Map<String, String> storeByPlatform = new HashMap<>();
        jdbc.queryForList("SELECT store_code, platform FROM nx_integration_stores WHERE tenant_id = ? ORDER BY created_at", tenantId).forEach(row -> {
            String platform = text(row, "platform");
            String storeId = ProductBackfill.storeId(text(row, "store_code"), platform);
            if (copiedStores.contains(storeId) && platform != null) {
                storeByPlatform.putIfAbsent(upper(platform), storeId);
            }
        });

        Map<String, Map<String, Object>> addressById = new HashMap<>();
        jdbc.queryForList("SELECT id, full_name, address_line1, address_line2, city, state, postal_code, country FROM nx_addresses "
                + "WHERE tenant_id = ?", tenantId).forEach(address -> addressById.put(String.valueOf(address.get("id")), address));

        Map<String, List<Map<String, Object>>> itemsByOrder = new HashMap<>();
        jdbc.queryForList("SELECT i.order_id, i.sku, i.product_name, i.quantity, i.unit_price, i.allocated_node_id, i.allocated_qty, i.fulfilled_qty "
                        + "FROM nx_order_items i JOIN nx_orders o ON o.id = i.order_id WHERE o.tenant_id = ? ORDER BY i.order_id, i.id", tenantId)
                .forEach(item -> itemsByOrder.computeIfAbsent(String.valueOf(item.get("order_id")), orderId -> new ArrayList<>()).add(item));

        List<String> currencies = jdbc.queryForList("SELECT default_currency FROM nx_company_settings WHERE tenant_id = ? "
                + "AND default_currency IS NOT NULL", String.class, tenantId);
        String defaultCurrency = currencies.isEmpty() ? null : upper(currencies.get(0));

        return new Lookups(copiedLegacyIds, partyByCustomer, shipToByParty, emailByParty, facilityByNode, productBySku, inventoryItemByProductFacility,
                storeByPlatform, addressById, itemsByOrder, defaultCurrency);
    }

    /** A two-column query as a map: the first value per key, in query order. */
    private Map<String, String> firstValuePerKey(String sql, Object parameter) {
        Map<String, String> valueByKey = new HashMap<>();
        jdbc.query(sql, resultSet -> {
            valueByKey.putIfAbsent(resultSet.getString(1), resultSet.getString(2));
        }, parameter);
        return valueByKey;
    }

    // ── old values to core values ──

    static String orderStatus(String legacyStatus) {
        if (legacyStatus == null) {
            return CoreStatus.Order.CREATED;
        }
        return switch (upper(legacyStatus)) {
            case "ALLOCATED", "PARTIALLY_ALLOCATED", "RELEASED", "PICKING", "PACKED" -> CoreStatus.Order.APPROVED;
            case "SHIPPED", "DELIVERED", "COMPLETED", "FULFILLED" -> CoreStatus.Order.COMPLETED;
            case "CANCELLED", "CANCELED" -> CoreStatus.Order.CANCELLED;
            case "FAILED", "ON_HOLD", "HOLD" -> CoreStatus.Order.HOLD;
            case "REJECTED" -> CoreStatus.Order.REJECTED;
            default -> CoreStatus.Order.CREATED;
        };
    }

    static String salesChannel(String channel) {
        if (channel == null) {
            return "UNKNWN_SALES_CHANNEL";
        }
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
        if (channel == null) {
            return "CHANNEL_ORD_ID";
        }
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
        if (channel == null) {
            return "EXT_OFFLINE";
        }
        return switch (channel) {
            case "SHOPIFY", "SHOPIFY_POS" -> "EXT_SHOPIFY";
            case "BIGCOMMERCE" -> "EXT_BIGCOMMERCE";
            case "AMAZON", "EBAY", "WALMART", "FLIPKART" -> "EXT_MARKETPLACE";
            case "B2B_PORTAL", "EDI" -> "EXT_BILLING_ACCOUNT";
            default -> "EXT_OFFLINE";
        };
    }

    static String paymentStatus(String legacyPaymentStatus) {
        if (legacyPaymentStatus == null) {
            return CoreStatus.Payment.NOT_RECEIVED;
        }
        return switch (upper(legacyPaymentStatus)) {
            case "PAID", "CAPTURED", "SETTLED" -> CoreStatus.Payment.SETTLED;
            case "AUTHORIZED" -> CoreStatus.Payment.AUTHORIZED;
            case "REFUNDED" -> CoreStatus.Payment.REFUNDED;
            case "DECLINED", "FAILED" -> CoreStatus.Payment.DECLINED;
            default -> CoreStatus.Payment.NOT_RECEIVED;
        };
    }

    static String shipmentMethod(String fulfillmentType) {
        if (fulfillmentType == null) {
            return "STANDARD";
        }
        return switch (upper(fulfillmentType)) {
            case "BOPIS", "PICKUP", "STORE_PICKUP", "CURBSIDE" -> "STOREPICKUP";
            case "EXPRESS" -> "EXPRESS";
            case "NEXT_DAY" -> "NEXT_DAY";
            case "SAME_DAY" -> "SAME_DAY";
            default -> "STANDARD";
        };
    }

    // ── reading old rows ──

    private static PartyService.AddressInput addressInput(Map<String, Object> address) {
        return new PartyService.AddressInput(text(address, "full_name"), text(address, "address_line1"), text(address, "address_line2"),
                text(address, "city"), text(address, "state"), text(address, "postal_code"), text(address, "country"), null, null, null);
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static LocalDateTime time(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    /** A number column; null is zero. */
    private static BigDecimal number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /** A text column; null or blank is null. */
    private static String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
