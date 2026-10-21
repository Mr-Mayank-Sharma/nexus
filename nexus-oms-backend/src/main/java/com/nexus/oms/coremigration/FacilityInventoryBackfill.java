package com.nexus.oms.coremigration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Copies one tenant's fulfilment nodes (nx_nodes) into facilities and its stock (nx_inventory) into inventory items.
 * A node's daily capacity becomes the facility's daily order limit. Each stock row becomes one item (per lot when it
 * has one) with a single MIGRATION detail row: on hand as it was, and available = on hand - allocated - reserved, so
 * held stock stays held. Safety stock and reorder levels go to ProductFacility. Run the product copy first: stock is
 * matched to products by SKU. Safe to run again: copied nodes (NEXUS_LEGACY_ID) and existing items are skipped.
 */
@Service
public class FacilityInventoryBackfill {

    private static final Logger log = LoggerFactory.getLogger(FacilityInventoryBackfill.class);
    static final String LEGACY_ID = "NEXUS_LEGACY_ID";
    private static final int CHUNK = 250;

    private final JdbcTemplate jdbc;
    private final FacilityService facilities;
    private final InventoryService inventory;
    private final TransactionTemplate tx;
    private final EntityManager em;
    private final ObjectMapper json = new ObjectMapper();

    public FacilityInventoryBackfill(JdbcTemplate jdbc, FacilityService facilities, InventoryService inventory,
                                     TransactionTemplate tx, EntityManager em) {
        this.jdbc = jdbc;
        this.facilities = facilities;
        this.inventory = inventory;
        this.tx = tx;
        this.em = em;
    }

    public record Result(int facilitiesScanned, int facilitiesCreated, int stockRowsScanned, int itemsCreated,
                         int itemsAlreadyCopied, int rowsWithoutLocation, int rowsWithUnknownSku) {
    }

    public Result run(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("Run the stock copy as a user of the company whose stock to copy");

        List<Map<String, Object>> nodes = jdbc.queryForList("SELECT id, name, type, address, latitude, longitude, is_active, "
                + "capacity_daily FROM nx_nodes WHERE tenant_id = ? ORDER BY name, id", tenantId);
        Map<String, String> facilityByNode = new HashMap<>();
        jdbc.queryForList("SELECT id_value, facility_id FROM facility_identification WHERE facility_iden_type_id = ? AND thru_date IS NULL", LEGACY_ID)
                .forEach(r -> facilityByNode.put(String.valueOf(r.get("id_value")), String.valueOf(r.get("facility_id"))));
        int facilitiesCreated = tx.execute(s -> {
            int n = 0;
            for (Map<String, Object> r : nodes) {
                String nodeId = String.valueOf(r.get("id"));
                if (facilityByNode.containsKey(nodeId)) continue;
                Number capacity = (Number) r.get("capacity_daily");
                String facilityId = facilities.create(new FacilityService.NewFacility(null, facilityType(str(r, "type")), str(r, "name"),
                        null, null, postalCode(str(r, "address")), (BigDecimal) r.get("latitude"), (BigDecimal) r.get("longitude"),
                        null, null, null, capacity == null || capacity.longValue() <= 0 ? null : capacity.longValue()));
                facilities.setIdentification(facilityId, LEGACY_ID, nodeId);
                facilityByNode.put(nodeId, facilityId);
                n++;
            }
            return n;
        });

        // Read once instead of querying per row: SKU -> product, and the items that already exist.
        Map<String, String> productBySku = new HashMap<>();
        jdbc.queryForList("SELECT id_value, product_id FROM good_identification WHERE good_identification_type_id = 'SKU' AND thru_date IS NULL")
                .forEach(r -> productBySku.putIfAbsent(String.valueOf(r.get("id_value")), String.valueOf(r.get("product_id"))));
        Set<String> existing = new HashSet<>();
        jdbc.queryForList("SELECT product_id, facility_id, lot_id FROM inventory_item")
                .forEach(r -> existing.add(key(String.valueOf(r.get("product_id")), String.valueOf(r.get("facility_id")), (String) r.get("lot_id"))));

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT sku, node_id, quantity_on_hand, quantity_allocated, quantity_reserved, "
                + "quantity_damaged, safety_stock, reorder_point, reorder_qty, lot_number, expiry_date FROM nx_inventory "
                + "WHERE tenant_id = ? ORDER BY sku, node_id, lot_number", tenantId);
        int created = 0, already = 0, noLocation = 0, unknownSku = 0;
        for (int from = 0; from < rows.size(); from += CHUNK) {
            List<Map<String, Object>> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK));
            int[] c = tx.execute(s -> {
                // new rows only (checked against the preloaded set), so flush once at commit
                em.setFlushMode(FlushModeType.COMMIT);
                return copy(chunk, facilityByNode, productBySku, existing);
            });
            created += c[0];
            already += c[1];
            noLocation += c[2];
            unknownSku += c[3];
        }
        log.info("Stock copy for tenant {}: {} nodes ({} new facilities), {} stock rows ({} items new, {} already copied, "
                + "{} without a location, {} with an unknown SKU)", tenantId, nodes.size(), facilitiesCreated, rows.size(),
                created, already, noLocation, unknownSku);
        return new Result(nodes.size(), facilitiesCreated, rows.size(), created, already, noLocation, unknownSku);
    }

    private int[] copy(List<Map<String, Object>> chunk, Map<String, String> facilityByNode, Map<String, String> productBySku, Set<String> existing) {
        int created = 0, already = 0, noLocation = 0, unknownSku = 0;
        for (Map<String, Object> r : chunk) {
            String facilityId = r.get("node_id") == null ? null : facilityByNode.get(String.valueOf(r.get("node_id")));
            if (facilityId == null) {
                noLocation++;
                continue;
            }
            String productId = productBySku.get(str(r, "sku"));
            if (productId == null) {
                unknownSku++;
                continue;
            }
            String lotId = lotId(str(r, "lot_number"));
            if (!existing.add(key(productId, facilityId, lotId))) {
                already++;
                continue;
            }
            BigDecimal onHand = num(r, "quantity_on_hand");
            BigDecimal available = onHand.subtract(num(r, "quantity_allocated")).subtract(num(r, "quantity_reserved"));
            BigDecimal damaged = num(r, "quantity_damaged");
            Object e = r.get("expiry_date");
            LocalDate expiry = e instanceof Date d ? d.toLocalDate() : e instanceof LocalDate d ? d : null;
            inventory.openingBalance(new InventoryService.Opening(productId, facilityId, lotId,
                            expiry == null ? null : expiry.atStartOfDay(), onHand, available, null, null,
                            damaged.signum() == 0 ? null : damaged.stripTrailingZeros().toPlainString() + " damaged in the earlier model"),
                    "IID_MIGRATION", InventoryService.Cause.note("Copied from the earlier Nexus model"));
            if (r.get("safety_stock") != null || r.get("reorder_point") != null || r.get("reorder_qty") != null) {
                inventory.setLevels(productId, facilityId, new InventoryService.Levels(dec(r, "safety_stock"), dec(r, "reorder_point"),
                        dec(r, "reorder_qty"), null, null, null));
            }
            created++;
        }
        return new int[]{created, already, noLocation, unknownSku};
    }

    static String facilityType(String nodeType) {
        if (nodeType == null) return "WAREHOUSE";
        return switch (nodeType.trim().toUpperCase(Locale.ROOT)) {
            case "STORE", "RETAIL", "RETAIL_STORE" -> "RETAIL_STORE";
            case "DC", "DISTRIBUTION_CENTER" -> "DISTRIBUTION_CENTER";
            case "DARK_STORE" -> "DARK_STORE";
            case "3PL", "THIRD_PARTY_LOGISTICS" -> "THIRD_PARTY_LOGISTICS";
            case "DROPSHIP", "SUPPLIER" -> "DROPSHIP";
            default -> "WAREHOUSE";
        };
    }

    /** Lot numbers become lot ids, cleaned to the allowed characters. */
    static String lotId(String lotNumber) {
        if (lotNumber == null) return null;
        String id = lotNumber.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        return id.isEmpty() ? null : id.length() > 40 ? id.substring(0, 40) : id;
    }

    private String postalCode(String address) {
        if (address == null) return null;
        try {
            JsonNode a = json.readTree(address);
            for (String k : new String[]{"postalCode", "postal_code", "zip", "zipCode", "zip_code", "pincode"}) {
                if (a.hasNonNull(k) && !a.get(k).asText().isBlank()) return a.get(k).asText().trim();
            }
        } catch (Exception ignored) {
            // an address that isn't JSON has no postal code we can read
        }
        return null;
    }

    private static String key(String productId, String facilityId, String lotId) {
        return productId + "|" + facilityId + "|" + (lotId == null ? "" : lotId);
    }

    private static BigDecimal num(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }

    private static BigDecimal dec(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }

    private static String str(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }
}
