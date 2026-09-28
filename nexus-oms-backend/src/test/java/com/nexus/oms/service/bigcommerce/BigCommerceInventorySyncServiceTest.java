package com.nexus.oms.service.bigcommerce;

import com.nexus.oms.entity.NxInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Storefront sellable-quantity maths for the BigCommerce push.
 *
 * <p>This is the value BigCommerce is told it may sell, so it has to agree with
 * {@code InventoryService#available} and {@code InventoryRepository#reserveAtomicByRow}: if the
 * storefront is allowed to sell units the internal allocator considers protected, the order lands
 * and immediately backorders. Everything is driven through rows rather than the HTTP client
 * because the arithmetic is the part that regresses.
 */
class BigCommerceInventorySyncServiceTest {

    private static NxInventory row(Integer onHand, Integer allocated, Integer reserved, Integer safety) {
        return NxInventory.builder()
                .id(UUID.randomUUID())
                .sku("SKU-1")
                .quantityOnHand(onHand)
                .quantityAllocated(allocated)
                .quantityReserved(reserved)
                .safetyStock(safety)
                .build();
    }

    @Test
    void subtractsSafetyStockFromOnHand() {
        // 100 - 0 - 0 - 20 = 80
        assertEquals(80, BigCommerceInventorySyncService.sellableToStorefront(
                List.of(row(100, 0, 0, 20))));
    }

    @Test
    void subtractsAllocatedAndReservedAndSafetyStock() {
        // 100 - 10 - 5 - 20 = 65
        assertEquals(65, BigCommerceInventorySyncService.sellableToStorefront(
                List.of(row(100, 10, 5, 20))));
    }

    @Test
    void aggregatesAcrossEveryHoldingRow() {
        // Net across rows, not row by row: -5 + 40 = 35
        assertEquals(35, BigCommerceInventorySyncService.sellableToStorefront(
                List.of(row(0, 0, 0, 5), row(40, 0, 0, 0))));
    }

    @Test
    void clampsNegativeNetToZero() {
        // BigCommerce rejects a negative inventory_level outright, so this must be 0 and not -25.
        assertEquals(0, BigCommerceInventorySyncService.sellableToStorefront(
                List.of(row(0, 0, 0, 25))));
    }

    @Test
    void treatsNullQuantityColumnsAsZero() {
        // Seeded/legacy rows can hold explicit NULLs; the previous mapToInt(NxInventory::get..)
        // unboxed these straight to int and threw NPE.
        assertEquals(0, BigCommerceInventorySyncService.sellableToStorefront(
                List.of(row(null, null, null, null))));
    }

    @Test
    void partialNullsAreTolerated() {
        // 10 on hand, everything else NULL => 10
        assertEquals(10, BigCommerceInventorySyncService.sellableToStorefront(
                List.of(row(10, null, null, null))));
    }

    @Test
    void emptyRowListIsZero() {
        assertEquals(0, BigCommerceInventorySyncService.sellableToStorefront(List.of()));
    }
}
