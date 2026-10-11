package com.nexus.oms.service;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxInventoryMovement;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.InventoryMovementRepository;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.bigcommerce.BigCommerceInventorySyncService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final BigCommerceInventorySyncService bigCommerceInventorySyncService;
    private final InventoryMovementRepository movementRepository;
    private final InventoryLedger ledger;
    private final TransactionTemplate tx;

    public InventoryService(InventoryRepository inventoryRepository,
                            BigCommerceInventorySyncService bigCommerceInventorySyncService,
                            InventoryMovementRepository movementRepository,
                            InventoryLedger ledger,
                            PlatformTransactionManager transactionManager) {
        this.inventoryRepository = inventoryRepository;
        this.bigCommerceInventorySyncService = bigCommerceInventorySyncService;
        this.movementRepository = movementRepository;
        this.ledger = ledger;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Delegates to {@link InventoryLedger}; kept here because most stock changes already go through this service. */
    public void recordMovement(NxInventory row, String type, int onHandDelta, int allocatedDelta, int reservedDelta,
                               String referenceType, String referenceId, String reason) {
        ledger.record(row, type, onHandDelta, allocatedDelta, reservedDelta, referenceType, referenceId, reason);
    }

    /** Every stock row against the ledger: a non-zero "unexplained" is a change nobody recorded. */
    public List<InventoryLedger.RowCheck> ledgerCheck(UUID tenantId) {
        return ledger.check(tenantId);
    }

    /** Writes opening-balance lines for stock that predates the ledger; returns how many rows it covered. */
    public int baselineLedger(UUID tenantId) {
        return ledger.baseline(tenantId);
    }

    /** Most recent ledger lines, newest first, optionally for one SKU and location. */
    public List<NxInventoryMovement> movements(UUID tenantId, String sku, UUID nodeId, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 500));
        if (sku != null && !sku.isBlank() && nodeId != null) {
            return movementRepository.findByTenantIdAndSkuAndNodeIdOrderByCreatedAtDesc(tenantId, sku, nodeId, page);
        }
        if (sku != null && !sku.isBlank()) {
            return movementRepository.findByTenantIdAndSkuOrderByCreatedAtDesc(tenantId, sku, page);
        }
        return movementRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory createInventory(NxInventory inventory) {
        NxInventory saved = inventoryRepository.save(inventory);
        recordMovement(saved, NxInventoryMovement.ADJUSTMENT, saved.getQuantityOnHand() == null ? 0 : saved.getQuantityOnHand(),
                saved.getQuantityAllocated() == null ? 0 : saved.getQuantityAllocated(), 0, "MANUAL", null, "Opening balance");
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

    public NxInventory adjustInventoryBySku(UUID tenantId, String sku, int quantityDelta) {
        return adjustInventoryBySku(tenantId, sku, null, quantityDelta, null);
    }

    /**
     * Manual adjustment of one stock row. A SKU is held per location, so when it exists at more than
     * one the caller must say which; the previous version silently adjusted whichever row the
     * database returned first.
     */
    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory adjustInventoryBySku(UUID tenantId, String sku, UUID nodeId, int quantityDelta, String reason) {
        List<NxInventory> rows = inventoryRepository.findByTenantIdAndSku(tenantId, sku);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        NxInventory inv;
        if (nodeId != null) {
            inv = rows.stream().filter(r -> nodeId.equals(r.getNodeId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No inventory for SKU " + sku + " at node " + nodeId));
        } else if (rows.size() == 1) {
            inv = rows.get(0);
        } else {
            throw new BadRequestException("SKU " + sku + " is held at " + rows.size()
                    + " locations; choose which location to adjust (nodeId)");
        }
        int newQty = nz(inv.getQuantityOnHand()) + quantityDelta;
        if (newQty < 0) {
            throw new BadRequestException("Insufficient inventory to reduce (SKU: " + sku + ")");
        }
        inv.setQuantityOnHand(newQty);
        NxInventory saved = inventoryRepository.save(inv);
        recordMovement(saved, NxInventoryMovement.ADJUSTMENT, quantityDelta, 0, 0, "MANUAL", null, reason);
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
        recordMovement(saved, NxInventoryMovement.ADJUSTMENT, quantityDelta, 0, 0, "STORE_FULFILLMENT", null, null);
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
        recordMovement(saved, NxInventoryMovement.ADJUSTMENT, quantityChange, 0, 0, "MANUAL", null, null);
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

    public void reserveInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        reserveInventory(tenantId, sku, nodeId, qty, null, null);
    }

    // The stock change and its ledger line commit together in one short transaction (joining the
    // caller's if there is one); the BigCommerce HTTP push happens after it, so no row lock is held
    // across a network call (which caused deadlocks under load).
    @CacheEvict(value = "inventory", allEntries = true)
    public void reserveInventory(UUID tenantId, String sku, UUID nodeId, int qty, String referenceType, String referenceId) {
        // Resolve the single backing row first (node-specific wins, else shared pool) and reserve
        // against it by id. A combined "nodeId = :id OR nodeId IS NULL" UPDATE would decrement the
        // node row AND the shared row by the full quantity when both exist.
        StockPosition position = resolveStock(tenantId, sku, nodeId);
        if (position.inventoryId() == null) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        tx.executeWithoutResult(status -> {
            // Conditional update: the availability guard is re-checked in the WHERE clause, so this
            // is safe under concurrency and needs no optimistic-lock retry.
            int updated = inventoryRepository.reserveAtomicByRow(position.inventoryId(), qty);
            if (updated == 0) {
                throw new BadRequestException("Insufficient inventory: available " + position.available()
                        + ", requested " + qty);
            }
            inventoryRepository.findById(position.inventoryId()).ifPresent(row ->
                    recordMovement(row, NxInventoryMovement.RESERVE, 0, qty, 0, referenceType, referenceId, null));
        });
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public void releaseInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        releaseInventory(tenantId, sku, nodeId, qty, null, null);
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public void releaseInventory(UUID tenantId, String sku, UUID nodeId, int qty, String referenceType, String referenceId) {
        // Same single-row resolution as reserve, so a release can only ever return units to the
        // exact row the reservation was taken from.
        StockPosition position = resolveStock(tenantId, sku, nodeId);
        if (position.inventoryId() == null) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        tx.executeWithoutResult(status -> {
            NxInventory before = inventoryRepository.findById(position.inventoryId()).orElse(null);
            int actuallyReleased = before == null ? 0 : Math.min(qty, nz(before.getQuantityAllocated()));
            inventoryRepository.releaseAtomicByRow(position.inventoryId(), qty);
            if (before != null) {
                recordMovement(before, NxInventoryMovement.RELEASE, 0, -actuallyReleased, 0, referenceType, referenceId, null);
            }
        });
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
    }

    /**
     * Stock physically leaves: takes {@code qty} off on-hand and off the order's allocation, and
     * writes the ledger line. Until this existed, shipping an order changed only its status, so
     * on-hand stock stayed overstated forever and the allocation was never cleared.
     *
     * <p>Quantities are clamped at zero rather than refused: the goods have already left, so the
     * books must follow, and a shortfall is recorded in the ledger line's reason so it is visible.
     * Uses the row's optimistic version, so a concurrent change fails loudly instead of being lost.
     *
     * @return units actually taken off on-hand
     */
    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public int consumeAllocated(UUID tenantId, String sku, UUID nodeId, int qty, String referenceType, String referenceId) {
        if (qty <= 0) return 0;
        StockPosition position = resolveStock(tenantId, sku, nodeId);
        if (position.inventoryId() == null) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        NxInventory inv = inventoryRepository.findById(position.inventoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", position.inventoryId()));
        int onHand = nz(inv.getQuantityOnHand());
        int allocated = nz(inv.getQuantityAllocated());
        int fromOnHand = Math.min(qty, onHand);
        int fromAllocated = Math.min(qty, allocated);
        inv.setQuantityOnHand(onHand - fromOnHand);
        inv.setQuantityAllocated(allocated - fromAllocated);
        inventoryRepository.save(inv);
        String shortfall = fromOnHand < qty ? "shipped " + qty + " but only " + fromOnHand + " were on hand" : null;
        recordMovement(inv, NxInventoryMovement.SHIPMENT, -fromOnHand, -fromAllocated, 0, referenceType, referenceId, shortfall);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
        return fromOnHand;
    }

    /**
     * A transfer leaves its source: takes {@code qty} off on-hand there and holds it as in-transit at
     * the destination until it is received. Only stock that is not already promised to an order can
     * be moved. Transfers used to change only their own status, so stock never actually moved.
     */
    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public void transferOut(UUID tenantId, String sku, UUID sourceNode, UUID destinationNode, int qty, String transferId) {
        if (qty <= 0) return;
        NxInventory source = inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, sourceNode)
                .orElseThrow(() -> new BadRequestException("No stock of " + sku + " at the source location"));
        int free = nz(source.getQuantityOnHand()) - nz(source.getQuantityAllocated()) - nz(source.getQuantityReserved());
        if (free < qty) {
            throw new BadRequestException("Only " + Math.max(free, 0) + " of " + sku
                    + " can be moved from the source (the rest is allocated to orders); requested " + qty);
        }
        source.setQuantityOnHand(nz(source.getQuantityOnHand()) - qty);
        recordMovement(inventoryRepository.save(source), NxInventoryMovement.TRANSFER_OUT, -qty, 0, 0, "TRANSFER", transferId, null);
        NxInventory destination = destinationRow(tenantId, sku, destinationNode);
        destination.setQuantityInTransit(nz(destination.getQuantityInTransit()) + qty);
        inventoryRepository.save(destination);
    }

    /** The goods arrive: adds what was received to on-hand and clears what had been in transit. */
    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public void transferIn(UUID tenantId, String sku, UUID destinationNode, int received, int shipped, String transferId) {
        NxInventory destination = destinationRow(tenantId, sku, destinationNode);
        destination.setQuantityOnHand(nz(destination.getQuantityOnHand()) + Math.max(received, 0));
        destination.setQuantityInTransit(Math.max(0, nz(destination.getQuantityInTransit()) - Math.max(shipped, 0)));
        NxInventory saved = inventoryRepository.save(destination);
        String note = received != shipped ? "received " + received + " of " + shipped + " shipped" : null;
        recordMovement(saved, NxInventoryMovement.TRANSFER_IN, Math.max(received, 0), 0, 0, "TRANSFER", transferId, note);
    }

    private NxInventory destinationRow(UUID tenantId, String sku, UUID nodeId) {
        return inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId).orElseGet(() ->
                inventoryRepository.save(NxInventory.builder().tenantId(tenantId).sku(sku).nodeId(nodeId)
                        .quantityOnHand(0).quantityAllocated(0).quantityReserved(0).quantityInTransit(0)
                        .quantityOnOrder(0).quantityDamaged(0).safetyStock(0).reorderPoint(0).reorderQty(0).build()));
    }

    /**
     * Puts returned units back on hand and writes the ledger line. They go to the location the order
     * shipped from when that is known; otherwise to the SKU's only location, or (a return must not be
     * refused over ambiguity) to the location holding the most of it.
     */
    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory restockReturn(UUID tenantId, String sku, UUID originNode, int qty, String returnId) {
        if (qty <= 0) throw new BadRequestException("A restock quantity must be positive");
        List<NxInventory> rows = inventoryRepository.findByTenantIdAndSku(tenantId, sku);
        NxInventory target = null;
        if (originNode != null) {
            target = rows.stream().filter(r -> originNode.equals(r.getNodeId())).findFirst().orElse(null);
        }
        if (target == null && rows.size() == 1) {
            target = rows.get(0);
        }
        if (target == null && !rows.isEmpty()) {
            target = rows.stream().max(java.util.Comparator.comparingInt(r -> nz(r.getQuantityOnHand()))).get();
        }
        if (target == null) {
            target = destinationRow(tenantId, sku, originNode);   // first stock this SKU has ever had
        }
        target.setQuantityOnHand(nz(target.getQuantityOnHand()) + qty);
        NxInventory saved = inventoryRepository.save(target);
        recordMovement(saved, NxInventoryMovement.RETURN, qty, 0, 0, "RETURN", returnId, null);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
        return saved;
    }
}
