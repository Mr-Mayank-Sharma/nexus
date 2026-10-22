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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nexus.oms.coremigration.LegacyRows.LEGACY_ID;
import static com.nexus.oms.coremigration.LegacyRows.number;
import static com.nexus.oms.coremigration.LegacyRows.numberOrNull;
import static com.nexus.oms.coremigration.LegacyRows.text;

/**
 * Copies one tenant's fulfilment nodes (nx_nodes) and warehouses (nx_warehouses) into facilities, and its stock
 * (nx_inventory) into inventory items.
 * <ul>
 *   <li>A node's daily capacity becomes the facility's daily order limit; a warehouse code is kept as the facility id
 *       when it can be.</li>
 *   <li>Each stock row becomes one item (per lot when it has one) with a single MIGRATION detail row: on hand as it was,
 *       and available = on hand - allocated - reserved, so held stock stays held.</li>
 *   <li>Safety stock and reorder levels go to ProductFacility.</li>
 * </ul>
 * Run the product copy first: stock is matched to products by SKU. Safe to run again: copied nodes (NEXUS_LEGACY_ID)
 * and existing items are skipped.
 */
@Service
public class FacilityInventoryBackfill {

    private static final Logger log = LoggerFactory.getLogger(FacilityInventoryBackfill.class);
    private static final int CHUNK_SIZE = 250;
    private static final int MAX_ID_LENGTH = 40;
    /** JSON keys an address may keep its postal code under. */
    private static final String[] POSTAL_CODE_KEYS = {"postalCode", "postal_code", "zip", "zipCode", "zip_code", "pincode"};

    private final JdbcTemplate jdbc;
    private final FacilityService facilityService;
    private final InventoryService inventoryService;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public FacilityInventoryBackfill(JdbcTemplate jdbc, FacilityService facilityService, InventoryService inventoryService,
                                     TransactionTemplate transaction, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.facilityService = facilityService;
        this.inventoryService = inventoryService;
        this.transaction = transaction;
        this.entityManager = entityManager;
    }

    public record Result(int facilitiesScanned, int facilitiesCreated, int stockRowsScanned, int itemsCreated,
                         int itemsAlreadyCopied, int rowsWithoutLocation, int rowsWithUnknownSku) {
    }

    /** Running totals of the stock copy, added up over the chunks. */
    private static final class Counts {
        int itemsCreated;
        int itemsAlreadyCopied;
        int rowsWithoutLocation;
        int rowsWithUnknownSku;

        void add(Counts other) {
            itemsCreated += other.itemsCreated;
            itemsAlreadyCopied += other.itemsAlreadyCopied;
            rowsWithoutLocation += other.rowsWithoutLocation;
            rowsWithUnknownSku += other.rowsWithUnknownSku;
        }
    }

    public Result run(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Run the stock copy as a user of the company whose stock to copy");
        }

        // Step 1: locations. Stock rows point at a fulfilment node or, in most data, a warehouse; both become facilities.
        List<Map<String, Object>> locations = jdbc.queryForList("SELECT id, NULL AS code, name, type, address, NULL AS zip_code, latitude, "
                + "longitude, capacity_daily FROM nx_nodes WHERE tenant_id = ? ORDER BY name, id", tenantId);
        locations.addAll(jdbc.queryForList("SELECT id, code, name, type, NULL AS address, zip_code, latitude, longitude, "
                + "NULL AS capacity_daily FROM nx_warehouses WHERE tenant_id = ? ORDER BY code, id", tenantId));
        Map<String, String> facilityByNode = new HashMap<>();
        jdbc.queryForList("SELECT id_value, facility_id FROM facility_identification WHERE facility_iden_type_id = ? AND thru_date IS NULL", LEGACY_ID)
                .forEach(row -> facilityByNode.put(String.valueOf(row.get("id_value")), String.valueOf(row.get("facility_id"))));
        int facilitiesCreated = transaction.execute(status -> copyLocations(locations, facilityByNode));

        // Step 2: read once instead of querying per row: SKU to product, and the items that already exist.
        Map<String, String> productBySku = new HashMap<>();
        jdbc.queryForList("SELECT id_value, product_id FROM good_identification WHERE good_identification_type_id = 'SKU' AND thru_date IS NULL")
                .forEach(row -> productBySku.putIfAbsent(String.valueOf(row.get("id_value")), String.valueOf(row.get("product_id"))));
        Set<String> existingItems = new HashSet<>();
        jdbc.queryForList("SELECT product_id, facility_id, lot_id FROM inventory_item")
                .forEach(row -> existingItems.add(itemKey(String.valueOf(row.get("product_id")), String.valueOf(row.get("facility_id")),
                        (String) row.get("lot_id"))));

        // Step 3: stock, in chunks of one transaction each.
        List<Map<String, Object>> stockRows = jdbc.queryForList("SELECT sku, node_id, quantity_on_hand, quantity_allocated, quantity_reserved, "
                + "quantity_damaged, safety_stock, reorder_point, reorder_qty, lot_number, expiry_date FROM nx_inventory "
                + "WHERE tenant_id = ? ORDER BY sku, node_id, lot_number", tenantId);
        Counts total = new Counts();
        for (int start = 0; start < stockRows.size(); start += CHUNK_SIZE) {
            List<Map<String, Object>> chunk = stockRows.subList(start, Math.min(stockRows.size(), start + CHUNK_SIZE));
            Counts chunkCounts = transaction.execute(status -> {
                // Only new rows (checked against the preloaded set), so one flush at commit is enough.
                entityManager.setFlushMode(FlushModeType.COMMIT);
                return copyStock(chunk, facilityByNode, productBySku, existingItems);
            });
            total.add(chunkCounts);
        }
        log.info("Stock copy for tenant {}: {} nodes ({} new facilities), {} stock rows ({} items new, {} already copied, "
                        + "{} without a location, {} with an unknown SKU)", tenantId, locations.size(), facilitiesCreated, stockRows.size(),
                total.itemsCreated, total.itemsAlreadyCopied, total.rowsWithoutLocation, total.rowsWithUnknownSku);
        return new Result(locations.size(), facilitiesCreated, stockRows.size(), total.itemsCreated, total.itemsAlreadyCopied,
                total.rowsWithoutLocation, total.rowsWithUnknownSku);
    }

    /** Creates a facility for each location not copied yet; returns how many. */
    private int copyLocations(List<Map<String, Object>> locations, Map<String, String> facilityByNode) {
        int created = 0;
        for (Map<String, Object> location : locations) {
            String nodeId = String.valueOf(location.get("id"));
            if (facilityByNode.containsKey(nodeId)) {
                continue;
            }
            Number dailyCapacity = (Number) location.get("capacity_daily");
            Long dailyOrderLimit = dailyCapacity == null || dailyCapacity.longValue() <= 0 ? null : dailyCapacity.longValue();
            String postalCode = text(location, "zip_code") != null ? text(location, "zip_code") : postalCodeIn(text(location, "address"));
            String code = text(location, "code");
            FacilityService.NewFacility newFacility = new FacilityService.NewFacility(readableId(code), facilityType(text(location, "type")),
                    text(location, "name"), null, code, postalCode, (BigDecimal) location.get("latitude"), (BigDecimal) location.get("longitude"),
                    null, null, null, dailyOrderLimit);
            String facilityId = facilityService.create(newFacility);
            facilityService.setIdentification(facilityId, LEGACY_ID, nodeId);
            facilityByNode.put(nodeId, facilityId);
            created++;
        }
        return created;
    }

    private Counts copyStock(List<Map<String, Object>> chunk, Map<String, String> facilityByNode, Map<String, String> productBySku,
                             Set<String> existingItems) {
        Counts counts = new Counts();
        for (Map<String, Object> stockRow : chunk) {
            Object nodeId = stockRow.get("node_id");
            String facilityId = nodeId == null ? null : facilityByNode.get(String.valueOf(nodeId));
            if (facilityId == null) {
                counts.rowsWithoutLocation++;
                continue;
            }
            String productId = productBySku.get(text(stockRow, "sku"));
            if (productId == null) {
                counts.rowsWithUnknownSku++;
                continue;
            }
            String lotId = lotId(text(stockRow, "lot_number"));
            boolean firstTimeSeen = existingItems.add(itemKey(productId, facilityId, lotId));
            if (!firstTimeSeen) {
                counts.itemsAlreadyCopied++;
                continue;
            }

            BigDecimal onHand = number(stockRow, "quantity_on_hand");
            BigDecimal available = onHand.subtract(number(stockRow, "quantity_allocated")).subtract(number(stockRow, "quantity_reserved"));
            BigDecimal damaged = number(stockRow, "quantity_damaged");
            LocalDate expiry = LegacyRows.date(stockRow, "expiry_date");
            String damagedNote = damaged.signum() == 0 ? null : damaged.stripTrailingZeros().toPlainString() + " damaged in the earlier model";
            InventoryService.Opening opening = new InventoryService.Opening(productId, facilityId, lotId, expiry == null ? null : expiry.atStartOfDay(),
                    onHand, available, null, null, damagedNote);
            inventoryService.openingBalance(opening, InventoryService.Reason.MIGRATION, InventoryService.Cause.note("Copied from the earlier Nexus model"));

            boolean hasLevels = stockRow.get("safety_stock") != null || stockRow.get("reorder_point") != null || stockRow.get("reorder_qty") != null;
            if (hasLevels) {
                inventoryService.setLevels(productId, facilityId, new InventoryService.Levels(numberOrNull(stockRow, "safety_stock"),
                        numberOrNull(stockRow, "reorder_point"), numberOrNull(stockRow, "reorder_qty"), null, null, null));
            }
            counts.itemsCreated++;
        }
        return counts;
    }

    static String facilityType(String nodeType) {
        if (nodeType == null) {
            return "WAREHOUSE";
        }
        return switch (nodeType.trim().toUpperCase(Locale.ROOT)) {
            case "STORE", "RETAIL", "RETAIL_STORE" -> "RETAIL_STORE";
            case "DC", "DISTRIBUTION_CENTER", "AUTOMATED_DC", "FULFILLMENT_CENTER" -> "DISTRIBUTION_CENTER";
            case "DARK_STORE" -> "DARK_STORE";
            case "3PL", "THIRD_PARTY_LOGISTICS" -> "THIRD_PARTY_LOGISTICS";
            case "DROPSHIP", "SUPPLIER" -> "DROPSHIP";
            default -> "WAREHOUSE";
        };
    }

    /** A warehouse code (WH-01) is kept as the facility id when it is a valid id that isn't taken yet; otherwise null (a new id). */
    private String readableId(String code) {
        if (code == null) {
            return null;
        }
        String candidate = code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]", "_");
        if (candidate.isEmpty() || candidate.length() > MAX_ID_LENGTH) {
            return null;
        }
        Long taken = jdbc.queryForObject("SELECT COUNT(*) FROM facility WHERE facility_id = ?", Long.class, candidate);
        return taken != null && taken > 0 ? null : candidate;
    }

    /** Lot numbers become lot ids, cleaned to the allowed characters and cut to 40. */
    static String lotId(String lotNumber) {
        if (lotNumber == null) {
            return null;
        }
        String cleaned = lotNumber.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_ID_LENGTH ? cleaned.substring(0, MAX_ID_LENGTH) : cleaned;
    }

    /** The postal code in an address kept as JSON; null when there is none or the address isn't JSON. */
    private String postalCodeIn(String addressJson) {
        if (addressJson == null) {
            return null;
        }
        try {
            JsonNode address = jsonMapper.readTree(addressJson);
            for (String key : POSTAL_CODE_KEYS) {
                if (address.hasNonNull(key) && !address.get(key).asText().isBlank()) {
                    return address.get(key).asText().trim();
                }
            }
        } catch (Exception notJson) {
            // an address that isn't JSON has no postal code we can read
        }
        return null;
    }

    private static String itemKey(String productId, String facilityId, String lotId) {
        return productId + "|" + facilityId + "|" + (lotId == null ? "" : lotId);
    }
}
