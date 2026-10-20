package com.nexus.oms.generic;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxNode;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.NodeRepository;
import com.nexus.oms.repository.ProductRepository;
import com.nexus.oms.service.InventoryService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stock counts from another system (an ERP, a POS, a 3PL): sets on-hand for a SKU at a location, always through
 * the ledger. Canonical fields: sku, quantity (on hand), location (the source system's location code or name).
 * A location resolves through the connection's {@code locations} map, then a Nexus location id, then a Nexus
 * location name. SKUs Nexus does not sell are reported, not created, and unknown stock is never treated as zero.
 */
@Component
public class GenericStockWriter {

    public enum Result { CREATED, UPDATED, UNCHANGED, WOULD_CREATE, WOULD_UPDATE }

    private final InventoryRepository inventory;
    private final InventoryService inventoryService;
    private final ProductRepository products;
    private final NodeRepository nodes;

    public GenericStockWriter(InventoryRepository inventory, InventoryService inventoryService, ProductRepository products, NodeRepository nodes) {
        this.inventory = inventory;
        this.inventoryService = inventoryService;
        this.products = products;
        this.nodes = nodes;
    }

    /** Resolves locations once per run. */
    public final class Run {
        private final UUID tenantId;
        private final Map<String, String> configured;
        private final Map<String, UUID> byKey = new HashMap<>();
        private final String connectionName;

        Run(UUID tenantId, Map<String, String> configured, String connectionName) {
            this.tenantId = tenantId;
            this.configured = configured;
            this.connectionName = connectionName;
            for (NxNode n : nodes.findByTenantId(tenantId)) {
                byKey.put(n.getId().toString().toLowerCase(Locale.ROOT), n.getId());
                if (n.getName() != null) byKey.putIfAbsent(n.getName().trim().toLowerCase(Locale.ROOT), n.getId());
            }
        }

        public UUID location(String source) {
            if (GenericOrderWriter.blank(source)) {
                // a feed without locations is only usable when the tenant has exactly one
                Set<UUID> all = new HashSet<>(byKey.values());
                return all.size() == 1 ? all.iterator().next() : null;
            }
            String mapped = configured.get(source.trim());
            String key = (mapped != null ? mapped : source).trim().toLowerCase(Locale.ROOT);
            return byKey.get(key);
        }

        /** Throws IllegalArgumentException with the reason when the row cannot be used. */
        public Result write(GenericMapper.Mapped m, boolean apply) {
            if (!m.ok()) throw new IllegalArgumentException(m.problem());
            String sku = m.get("sku") == null ? "" : m.get("sku").trim();
            if (sku.isEmpty()) throw new IllegalArgumentException("sku is empty");
            Integer qty = GenericOrderWriter.intOrNull(m.get("quantity"));
            if (qty == null) throw new IllegalArgumentException("quantity is not a whole number (got '" + m.get("quantity") + "')");
            qty = Math.max(0, qty);
            UUID node = location(m.get("location"));
            if (node == null) {
                throw new IllegalArgumentException(GenericOrderWriter.blank(m.get("location"))
                        ? "no location in the row, and Nexus has more than one location"
                        : "location '" + m.get("location") + "' is not mapped to a Nexus location");
            }
            if (!products.existsByTenantIdAndSku(tenantId, sku)) throw new IllegalArgumentException("SKU " + sku + " is not in the Nexus catalogue");
            Optional<NxInventory> row = inventory.findByTenantIdAndSkuAndNodeId(tenantId, sku, node);
            if (row.isEmpty()) {
                if (!apply) return Result.WOULD_CREATE;
                inventoryService.importOpeningBalance(NxInventory.builder()
                        .tenantId(tenantId).sku(sku).nodeId(node).quantityOnHand(qty)
                        .quantityAllocated(0).quantityReserved(0).quantityInTransit(0)
                        .quantityOnOrder(0).quantityDamaged(0).safetyStock(0).reorderPoint(0).reorderQty(0)
                        .build(), "Opening balance from " + connectionName);
                return Result.CREATED;
            }
            int ours = row.get().getQuantityOnHand() == null ? 0 : row.get().getQuantityOnHand();
            if (ours == qty) return Result.UNCHANGED;
            if (!apply) return Result.WOULD_UPDATE;
            inventoryService.adjustInventoryBySku(tenantId, sku, node, qty - ours, "Stock count from " + connectionName);
            return Result.UPDATED;
        }
    }

    public Run start(UUID tenantId, Map<String, String> locations, String connectionName) {
        return new Run(tenantId, locations, connectionName);
    }
}
