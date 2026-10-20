package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.entity.Address;
import com.nexus.oms.entity.NxCustomer;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.repository.AddressRepository;
import com.nexus.oms.repository.CustomerRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Turns a mapped order into a Nexus order. The canonical fields a mapping can fill:
 * <pre>
 * orderId (required: the source system's id, used to never import an order twice), orderNumber, status,
 * currency, subtotal, shipping, tax, total, email, customerName, phone,
 * shipName, shipLine1, shipLine2, shipCity, shipState, shipPostalCode, shipCountry,
 * orderType (B2C or B2B), requestedShipDate, fulfillmentType (STANDARD, BOPIS, SHIP_FROM_STORE)
 * lines: sku (required), quantity (required), name, unitPrice
 * </pre>
 * Each order is saved in its own transaction, so one bad order never undoes the others.
 */
@Component
public class GenericOrderWriter {

    public enum Result { CREATED, ALREADY_IMPORTED, WOULD_CREATE }

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final CustomerRepository customers;
    private final AddressRepository addresses;
    private final TransactionTemplate tx;

    private com.nexus.oms.service.FulfilmentReleaseService release;

    /** Orders with a far ship date are held for fulfilment (optional so tests can leave it out). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRelease(com.nexus.oms.service.FulfilmentReleaseService release) { this.release = release; }

    public GenericOrderWriter(OrderRepository orders, OrderItemRepository items, CustomerRepository customers,
                              AddressRepository addresses, PlatformTransactionManager txm) {
        this.orders = orders;
        this.items = items;
        this.customers = customers;
        this.addresses = addresses;
        this.tx = new TransactionTemplate(txm);
    }

    /** Checks a mapped order; returns the problems (empty = fine). Used by preview and before every write. */
    public static List<String> problems(GenericMapper.Mapped m) {
        List<String> p = new ArrayList<>();
        if (!m.ok()) { p.add(m.problem()); return p; }
        if (blank(m.get("orderId"))) p.add("orderId is empty");
        if (m.lines().isEmpty()) p.add("the order has no lines");
        int i = 0;
        for (Map<String, String> l : m.lines()) {
            i++;
            if (blank(l.get("sku"))) p.add("line " + i + ": sku is empty");
            Integer q = intOrNull(l.get("quantity"));
            if (q == null || q <= 0) p.add("line " + i + ": quantity must be a whole number above 0 (got '" + l.get("quantity") + "')");
            if (!blank(l.get("unitPrice")) && dec(l.get("unitPrice")) == null) p.add("line " + i + ": unitPrice is not a number");
        }
        for (String f : List.of("subtotal", "shipping", "tax", "total")) {
            if (!blank(m.get(f)) && dec(m.get(f)) == null) p.add(f + " is not a number");
        }
        String cur = m.get("currency");
        if (!blank(cur) && !cur.trim().matches("[A-Za-z]{3}")) p.add("currency must be a 3-letter code (got '" + cur + "')");
        String type = m.get("orderType");
        if (!blank(type) && !Set.of("B2C", "B2B").contains(type.trim().toUpperCase(Locale.ROOT))) p.add("orderType must be B2C or B2B");
        if (!blank(m.get("requestedShipDate"))) {
            try { ValueExpr.toIso(m.get("requestedShipDate").trim()); } catch (Exception e) { p.add("requestedShipDate is not a date"); }
        }
        return p;
    }

    public Result write(UUID tenantId, String channel, String connectionName, GenericMapper.Mapped m, boolean apply) {
        String orderId = m.get("orderId").trim();
        String ch = blank(channel) ? "GENERIC" : channel.trim().toUpperCase(Locale.ROOT);
        if (orders.findByTenantIdAndChannelAndChannelOrderId(tenantId, ch, orderId).isPresent()) return Result.ALREADY_IMPORTED;
        if (!apply) return Result.WOULD_CREATE;
        tx.executeWithoutResult(s -> save(tenantId, ch, connectionName, m, orderId));
        return Result.CREATED;
    }

    private void save(UUID tenantId, String channel, String connectionName, GenericMapper.Mapped m, String orderId) {
        NxCustomer customer = customer(tenantId, m);
        Address ship = null;
        if (!blank(m.get("shipLine1")) || !blank(m.get("shipCity")) || !blank(m.get("shipPostalCode"))) {
            ship = addresses.save(Address.builder()
                    .tenantId(tenantId).addressType("SHIPPING")
                    .fullName(m.get("shipName")).phone(m.get("phone"))
                    .addressLine1(m.get("shipLine1")).addressLine2(m.get("shipLine2"))
                    .city(m.get("shipCity")).state(m.get("shipState")).postalCode(m.get("shipPostalCode"))
                    .country(blank(m.get("shipCountry")) ? null : m.get("shipCountry").trim())
                    .build());
        }
        BigDecimal linesTotal = BigDecimal.ZERO;
        for (Map<String, String> l : m.lines()) {
            BigDecimal price = Optional.ofNullable(dec(l.get("unitPrice"))).orElse(BigDecimal.ZERO);
            linesTotal = linesTotal.add(price.multiply(BigDecimal.valueOf(intOrNull(l.get("quantity")))));
        }
        BigDecimal subtotal = Optional.ofNullable(dec(m.get("subtotal"))).orElse(linesTotal);
        BigDecimal shipping = Optional.ofNullable(dec(m.get("shipping"))).orElse(BigDecimal.ZERO);
        BigDecimal tax = Optional.ofNullable(dec(m.get("tax"))).orElse(BigDecimal.ZERO);
        BigDecimal total = Optional.ofNullable(dec(m.get("total"))).orElse(subtotal.add(shipping).add(tax));

        ObjectNode meta = GenericConfig.JSON.createObjectNode();
        meta.put("source", "generic");
        meta.put("connection", connectionName);
        meta.put("orderType", blank(m.get("orderType")) ? "B2C" : m.get("orderType").trim().toUpperCase(Locale.ROOT));
        if (!blank(m.get("requestedShipDate"))) meta.put("requestedShipDate", ValueExpr.toIso(m.get("requestedShipDate").trim()));
        if (!blank(m.get("status"))) meta.put("sourceStatus", m.get("status").trim());
        meta.put("importedAt", Instant.now().toString());

        NxOrder order = NxOrder.builder()
                .tenantId(tenantId).channel(channel).channelOrderId(orderId)
                .externalId(blank(m.get("orderNumber")) ? orderId : m.get("orderNumber").trim())
                .customerId(customer == null ? null : customer.getId())
                .customerEmail(blank(m.get("email")) ? null : m.get("email").trim())
                .status(mapStatus(m.get("status")))
                .fulfillmentType(blank(m.get("fulfillmentType")) ? "STANDARD" : m.get("fulfillmentType").trim().toUpperCase(Locale.ROOT))
                .shipToAddress(ship)
                .currency(blank(m.get("currency")) ? "USD" : m.get("currency").trim().toUpperCase(Locale.ROOT))
                .subtotal(subtotal).shippingCost(shipping).taxAmount(tax).total(total)
                .metadata(meta.toString())
                .orderType(meta.get("orderType").asText())
                .requestedShipDate(blank(m.get("requestedShipDate")) ? null
                        : java.time.LocalDateTime.ofInstant(java.time.Instant.parse(ValueExpr.toIso(m.get("requestedShipDate").trim())), java.time.ZoneOffset.UTC))
                .build();
        if (release != null) release.plan(order);
        if (ship != null) order.setShipToAddressId(ship.getId());
        NxOrder saved = orders.save(order);
        for (Map<String, String> l : m.lines()) {
            int qty = intOrNull(l.get("quantity"));
            BigDecimal price = Optional.ofNullable(dec(l.get("unitPrice"))).orElse(BigDecimal.ZERO);
            items.save(NxOrderItem.builder()
                    .orderId(saved.getId()).sku(l.get("sku").trim())
                    .productName(blank(l.get("name")) ? l.get("sku").trim() : l.get("name").trim())
                    .quantity(qty).unitPrice(price).totalPrice(price.multiply(BigDecimal.valueOf(qty)))
                    .allocatedQty(0)
                    .build());
        }
    }

    private NxCustomer customer(UUID tenantId, GenericMapper.Mapped m) {
        String email = blank(m.get("email")) ? null : m.get("email").trim().toLowerCase(Locale.ROOT);
        String name = blank(m.get("customerName")) ? (email != null ? email : "Customer " + m.get("orderId")) : m.get("customerName").trim();
        if (email != null) {
            List<NxCustomer> found = customers.findAllByTenantIdAndEmail(tenantId, email);
            if (!found.isEmpty()) return found.get(0);
        }
        return customers.save(NxCustomer.builder().tenantId(tenantId).name(name).email(email).phone(m.get("phone")).build());
    }

    /** Cancelled and refunded orders keep their state; everything else enters Nexus as PENDING. */
    static String mapStatus(String source) {
        if (blank(source)) return "PENDING";
        String s = source.trim().toLowerCase(Locale.ROOT);
        if (s.contains("cancel") || s.contains("void")) return "CANCELLED";
        if (s.contains("refund")) return "RETURNED";
        return "PENDING";
    }

    static boolean blank(String s) { return s == null || s.isBlank(); }

    static Integer intOrNull(String s) {
        if (blank(s)) return null;
        try { return new BigDecimal(s.trim()).intValueExact(); } catch (Exception e) { return null; }
    }

    static BigDecimal dec(String s) {
        if (blank(s)) return null;
        try { return new BigDecimal(s.trim()); } catch (Exception e) { return null; }
    }
}
