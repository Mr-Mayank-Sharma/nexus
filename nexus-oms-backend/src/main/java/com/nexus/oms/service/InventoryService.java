package com.nexus.oms.service;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.service.bigcommerce.BigCommerceInventorySyncService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final BigCommerceInventorySyncService bigCommerceInventorySyncService;

    public InventoryService(InventoryRepository inventoryRepository,
                            BigCommerceInventorySyncService bigCommerceInventorySyncService) {
        this.inventoryRepository = inventoryRepository;
        this.bigCommerceInventorySyncService = bigCommerceInventorySyncService;
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory createInventory(NxInventory inventory) {
        NxInventory saved = inventoryRepository.save(inventory);
        bigCommerceInventorySyncService.pushSkuInventory(saved.getTenantId(), saved.getSku());
        return saved;
    }

    @Cacheable(value = "inventory", key = "#tenantId")
    public List<NxInventory> getInventoryByTenant(UUID tenantId) {
        return inventoryRepository.findByTenantId(tenantId);
    }

    @Cacheable(value = "inventory", key = "#tenantId + ':' + #sku")
    public NxInventory getBySku(UUID tenantId, String sku) {
        List<NxInventory> items = inventoryRepository.findByTenantIdAndSku(tenantId, sku);
        if (items.isEmpty()) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        return items.get(0);
    }

    @Cacheable(value = "inventory", key = "'atp:' + #tenantId + ':' + #sku")
    public Integer getAvailableToPromise(UUID tenantId, String sku) {
        return inventoryRepository.getAvailableToPromise(tenantId, sku);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory adjustInventoryBySku(UUID tenantId, String sku, int quantityDelta) {
        NxInventory inv = getBySku(tenantId, sku);
        int newQty = inv.getQuantityOnHand() + quantityDelta;
        if (newQty < 0) {
            throw new BadRequestException("Insufficient inventory to reduce (SKU: " + sku + ")");
        }
        inv.setQuantityOnHand(newQty);
        NxInventory saved = inventoryRepository.save(inv);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
        return saved;
    }

    /**
     * Location-scoped deduction for ship-from-store / endless-aisle flows.
     * Deducts sellable stock (quantityOnHand) at a specific store node and
     * fails fast when the node has insufficient stock for the SKU.
     */
    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory adjustInventoryBySkuAtNode(UUID tenantId, String sku, UUID nodeId, int quantityDelta) {
        NxInventory inv = inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No inventory for SKU " + sku + " at node " + nodeId));
        int newQty = inv.getQuantityOnHand() + quantityDelta;
        if (newQty < 0) {
            throw new BadRequestException("Insufficient inventory at node " + nodeId
                    + " for SKU " + sku + " (on hand: " + inv.getQuantityOnHand() + ")");
        }
        inv.setQuantityOnHand(newQty);
        NxInventory saved = inventoryRepository.save(inv);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory adjustInventory(UUID id, int quantityChange) {
        NxInventory inv = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));

        int newQty = inv.getQuantityOnHand() + quantityChange;
        if (newQty < 0) {
            throw new BadRequestException("Insufficient inventory to reduce");
        }
        inv.setQuantityOnHand(newQty);
        NxInventory saved = inventoryRepository.save(inv);
        bigCommerceInventorySyncService.pushSkuInventory(inv.getTenantId(), inv.getSku());
        return saved;
    }

    /**
     * Whether {@code qty} units of {@code sku} can be sourced for {@code nodeId}.
     *
     * <p>Delegates to {@link #resolveStock} so this agrees exactly with what reserve/release
     * actually mutate. The previous fallback was {@code anyMatch} over every tenant+SKU row,
     * which could report availability from a DIFFERENT node's stock; the allocator would then pick
     * the node believing it was covered, and the failure only surfaced at reservation time.
     */
    @Cacheable(value = "inventory", key = "'check:' + #tenantId + ':' + #sku + ':' + #nodeId + ':' + #qty")
    @Transactional
    public boolean checkAvailability(UUID tenantId, String sku, UUID nodeId, int qty) {
        return qty > 0 && resolveStock(tenantId, sku, nodeId).available() >= qty;
    }

    /**
     * Resolves the inventory row that backs a (tenant, sku, node) request and reports how much of
     * it is still allocatable.
     *
     * <p>Resolution order mirrors {@link #checkAvailability}: a node-specific row wins, otherwise
     * the shared tenant+SKU pool is used. Callers that allocate across several nodes need to know
     * which of the two they got, because every node without its own row resolves to the SAME shared
     * row — without that signal a multi-node allocation would each see the full pool and commit it
     * N times over.
     */
    public StockPosition resolveStock(UUID tenantId, String sku, UUID nodeId) {
        Optional<NxInventory> nodeRow = nodeId != null
                ? inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId)
                : Optional.empty();
        if (nodeRow.isPresent()) {
            NxInventory inv = nodeRow.get();
            return new StockPosition(inv.getId(), available(inv), false);
        }
        NxInventory shared = inventoryRepository.findByTenantIdAndSku(tenantId, sku).stream()
                .filter(inv -> inv.getNodeId() == null)
                .findFirst()
                .orElse(null);
        if (shared == null) {
            return new StockPosition(null, 0, true);
        }
        return new StockPosition(shared.getId(), available(shared), true);
    }

    /**
     * @param inventoryId  backing inventory row, or {@code null} when the SKU has no inventory at all
     * @param available    quantity still free to allocate at this node
     * @param fromSharedPool true when the node has no dedicated row and the shared pool was used
     */
    public record StockPosition(UUID inventoryId, int available, boolean fromSharedPool) {}

    private int available(NxInventory inv) {
        // Null-safe: seeded rows may have NULL quantity columns (schema default is 0 but
        // legacy/seed data can contain explicit NULLs). Treat NULL as 0.
        //
        // Safety stock is withheld, NOT allocatable. It is the buffer that stops a node selling
        // into a stockout when the next replenishment is late. Subtracting it here keeps
        // checkAvailability(), resolveStock() and — through position.available() — the T-17
        // allocator in agreement that the last few units are off-limits to order allocation.
        //
        // MUST stay in lockstep with InventoryRepository#reserveAtomicByRow: that JPQL guard is the
        // real enforcement point, because reserveInventory() has no Java-side precondition and only
        // inspects the affected-row count afterwards. Changing one without the other would let the
        // database hand out units that this method is simultaneously reporting as unavailable.
        int allocatable = nz(inv.getQuantityOnHand())
                - nz(inv.getQuantityAllocated())
                - nz(inv.getQuantityReserved())
                - nz(inv.getSafetyStock());

        // Never report negative availability. Safety stock can legitimately exceed what is on hand
        // (a fresh SKU carrying a global safety setting), and a negative figure is meaningless to
        // every caller. Clamping at zero preserves the "cannot fulfil" signal just as faithfully.
        return Math.max(0, allocatable);
    }

    private static int nz(Integer value) {
        return value != null ? value : 0;
    }

    // NOTE: no @Transactional — the atomic UPDATE commits immediately so row locks are never
    // held during the external BigCommerce HTTP push below (which caused deadlocks under load).
    @CacheEvict(value = "inventory", allEntries = true)
    public void reserveInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        // Resolve the single backing row first (node-specific wins, else shared pool) and reserve
        // against it by id. A combined "nodeId = :id OR nodeId IS NULL" UPDATE would decrement the
        // node row AND the shared row by the full quantity when both exist.
        StockPosition position = resolveStock(tenantId, sku, nodeId);
        if (position.inventoryId() == null) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        // Conditional update — the availability guard is re-checked in the WHERE clause, so this is
        // safe under concurrency and needs no optimistic-lock retry.
        int updated = inventoryRepository.reserveAtomicByRow(position.inventoryId(), qty);
        if (updated == 0) {
            throw new BadRequestException("Insufficient inventory: available " + position.available()
                    + ", requested " + qty);
        }
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public void releaseInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        // Same single-row resolution as reserve, so a release can only ever return units to the
        // exact row the reservation was taken from.
        StockPosition position = resolveStock(tenantId, sku, nodeId);
        if (position.inventoryId() == null) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        inventoryRepository.releaseAtomicByRow(position.inventoryId(), qty);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
    }
}
