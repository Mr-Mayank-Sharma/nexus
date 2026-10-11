package com.nexus.oms.service;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxInventoryMovement;
import com.nexus.oms.repository.InventoryMovementRepository;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the inventory ledger and checks it against the stock rows. Depends only on repositories, so
 * any service that changes stock (receiving, counting, importing) can use it without a cycle.
 */
@Component
public class InventoryLedger {

    /** A stock row, what it holds, and how much of that the ledger explains. */
    public record RowCheck(UUID inventoryId, String sku, UUID nodeId, int onHand, long ledgerOnHand, long unexplained) {
    }

    private final InventoryMovementRepository movementRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryLedger(InventoryMovementRepository movementRepository, InventoryRepository inventoryRepository) {
        this.movementRepository = movementRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /** One ledger line. Call in the same transaction as the stock change it describes. */
    public void record(NxInventory row, String type, int onHandDelta, int allocatedDelta, int reservedDelta,
                       String referenceType, String referenceId, String reason) {
        if (onHandDelta == 0 && allocatedDelta == 0 && reservedDelta == 0) return;
        movementRepository.save(NxInventoryMovement.builder()
                .tenantId(row.getTenantId())
                .inventoryId(row.getId())
                .sku(row.getSku())
                .nodeId(row.getNodeId())
                .movementType(type)
                .onHandDelta(onHandDelta)
                .allocatedDelta(allocatedDelta)
                .reservedDelta(reservedDelta)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .reason(reason)
                .createdBy(TenantContext.getCurrentUsername())
                .build());
    }

    /** For every stock row: on-hand versus the sum of its ledger deltas. Non-zero "unexplained" means unrecorded change. */
    public List<RowCheck> check(UUID tenantId) {
        Map<UUID, Long> sums = new HashMap<>();
        for (Object[] r : movementRepository.sumOnHandByInventory(tenantId)) {
            if (r[0] != null) sums.put((UUID) r[0], ((Number) r[1]).longValue());
        }
        return inventoryRepository.findByTenantId(tenantId).stream().map(row -> {
            int onHand = row.getQuantityOnHand() == null ? 0 : row.getQuantityOnHand();
            long ledger = sums.getOrDefault(row.getId(), 0L);
            return new RowCheck(row.getId(), row.getSku(), row.getNodeId(), onHand, ledger, onHand - ledger);
        }).toList();
    }

    /**
     * Writes an opening-balance line for every row the ledger does not yet explain, so stock that
     * predates the ledger starts reconciled. Lines are tagged BASELINE: it is a statement of the
     * balance at that moment, not a claim that the units arrived then.
     */
    @Transactional
    public int baseline(UUID tenantId) {
        int written = 0;
        for (RowCheck c : check(tenantId)) {
            if (c.unexplained() == 0) continue;
            NxInventory row = inventoryRepository.findById(c.inventoryId()).orElse(null);
            if (row == null) continue;
            record(row, NxInventoryMovement.ADJUSTMENT, (int) c.unexplained(), 0, 0, "BASELINE", null,
                    "Opening balance: stock that predates the ledger");
            written++;
        }
        return written;
    }
}
