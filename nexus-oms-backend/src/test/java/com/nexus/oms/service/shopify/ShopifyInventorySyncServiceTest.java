package com.nexus.oms.service.shopify;

import com.nexus.oms.entity.NxInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Storefront sellable-quantity maths for the Shopify push.
 *
 * <p>Intentionally the same contract as {@code BigCommerceInventorySyncServiceTest}: both channels
 * must publish exactly what {@code InventoryService#available} would let the allocator promise, or
 * the storefront will sell stock the order allocator then treats as protected. Keep the two in step.
 */
class ShopifyInventorySyncServiceTest {

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
        assertEquals(80, ShopifyInventorySyncService.sellableToStorefront(
                List.of(row(100, 0, 0, 20))));
    }

    @Test
    void subtractsAllocatedAndReservedAndSafetyStock() {
        // 100 - 10 - 5 - 20 = 65
        assertEquals(65, ShopifyInventorySyncService.sellableToStorefront(
                List.of(row(100, 10, 5, 20))));
    }

    @Test
    void aggregatesAcrossEveryHoldingRow() {
        // Net across rows, not row by row: -5 + 40 = 35
        assertEquals(35, ShopifyInventorySyncService.sellableToStorefront(
                List.of(row(0, 0, 0, 5), row(40, 0, 0, 0))));
    }

    @Test
    void clampsNegativeNetToZero() {
        assertEquals(0, ShopifyInventorySyncService.sellableToStorefront(
                List.of(row(0, 0, 0, 25))));
    }

    @Test
    void treatsNullQuantityColumnsAsZero() {
        // The previous mapToInt(NxInventory::getQuantityOnHand) threw NPE on a NULL column.
        assertEquals(0, ShopifyInventorySyncService.sellableToStorefront(
                List.of(row(null, null, null, null))));
    }

    @Test
    void partialNullsAreTolerated() {
        assertEquals(10, ShopifyInventorySyncService.sellableToStorefront(
                List.of(row(10, null, null, null))));
    }

    @Test
    void emptyRowListIsZero() {
        assertEquals(0, ShopifyInventorySyncService.sellableToStorefront(List.of()));
    }
}
