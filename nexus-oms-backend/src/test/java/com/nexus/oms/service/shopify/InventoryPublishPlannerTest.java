package com.nexus.oms.service.shopify;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.service.shopify.InventoryPublishPlanner.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The aggregation rules that decide what a sales channel may sell. These are the cases the
 * original single-sum implementation got wrong: stock of unmapped stores leaking into the
 * channel, an oversold node cancelling another node's real stock, and units offered twice.
 */
class InventoryPublishPlannerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final UUID ONLINE_WH = UUID.randomUUID();
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID UNMAPPED = UUID.randomUUID();

    private static final Map<UUID, Node> NODES = Map.of(
            ONLINE_WH, new Node(ONLINE_WH, "Online warehouse", true),
            STORE_A, new Node(STORE_A, "Store A", true),
            STORE_B, new Node(STORE_B, "Store B", true),
            UNMAPPED, new Node(UNMAPPED, "Back office", true));

    private static NxInventory row(UUID node, Integer onHand, Integer allocated, Integer reserved, Integer damaged, Integer safety) {
        return NxInventory.builder().id(UUID.randomUUID()).sku("SKU-1").nodeId(node).quantityOnHand(onHand)
                .quantityAllocated(allocated).quantityReserved(reserved).quantityDamaged(damaged).safetyStock(safety).build();
    }

    private static Rule rule(UUID node, String location) {
        return new Rule(node, location, true, 0, null);
    }

    private static LocationQuantity at(List<LocationQuantity> plan, String location) {
        return plan.stream().filter(p -> p.externalLocationId().equals(location)).findFirst().orElseThrow();
    }

    @Test
    void eachNodePublishesToItsOwnLocation_neverTheGrandTotalEverywhere() {
        List<NxInventory> rows = List.of(row(STORE_A, 30, 0, 0, 0, 0), row(STORE_B, 50, 0, 0, 0, 0));

        List<LocationQuantity> plan = InventoryPublishPlanner.plan("SKU-1", rows,
                List.of(rule(STORE_A, "111"), rule(STORE_B, "222")), NODES, TODAY);

        assertEquals(30, at(plan, "111").quantity());
        assertEquals(50, at(plan, "222").quantity());
    }

    @Test
    void severalNodesFeedingOnePooledLocation_areSummed() {
        List<NxInventory> rows = List.of(row(STORE_A, 30, 0, 0, 0, 0), row(STORE_B, 50, 0, 0, 0, 0), row(ONLINE_WH, 100, 0, 0, 0, 0));

        List<LocationQuantity> plan = InventoryPublishPlanner.plan("SKU-1", rows,
                List.of(rule(STORE_A, "900"), rule(STORE_B, "900")), NODES, TODAY);

        assertEquals(1, plan.size());
        assertEquals(80, at(plan, "900").quantity(), "the online warehouse is not mapped, so its 100 are not offered");
    }

    @Test
    void unmappedNodes_neverContribute() {
        List<NxInventory> rows = List.of(row(UNMAPPED, 999, 0, 0, 0, 0), row(STORE_A, 5, 0, 0, 0, 0));

        List<LocationQuantity> plan = InventoryPublishPlanner.plan("SKU-1", rows, List.of(rule(STORE_A, "111")), NODES, TODAY);

        assertEquals(5, at(plan, "111").quantity());
    }

    @Test
    void anOversoldNode_cannotCancelAnotherNodesRealStock() {
        // Store A is short (reserved 20 > on hand 10); Store B has 40. Summing first would give 30.
        List<NxInventory> rows = List.of(row(STORE_A, 10, 0, 20, 0, 0), row(STORE_B, 40, 0, 0, 0, 0));

        List<LocationQuantity> plan = InventoryPublishPlanner.plan("SKU-1", rows,
                List.of(rule(STORE_A, "900"), rule(STORE_B, "900")), NODES, TODAY);

        assertEquals(40, at(plan, "900").quantity());
    }

    @Test
    void subtractsAllocatedReservedDamagedAndSafetyStock() {
        List<NxInventory> rows = List.of(row(STORE_A, 100, 10, 5, 3, 7));

        assertEquals(75, at(InventoryPublishPlanner.plan("SKU-1", rows, List.of(rule(STORE_A, "111")), NODES, TODAY), "111").quantity());
    }

    @Test
    void treatsNullQuantityColumnsAsZero() {
        List<NxInventory> rows = List.of(row(STORE_A, 10, null, null, null, null), row(STORE_A, null, null, null, null, null));

        assertEquals(10, at(InventoryPublishPlanner.plan("SKU-1", rows, List.of(rule(STORE_A, "111")), NODES, TODAY), "111").quantity());
    }

    @Test
    void lotsAreCombinedPerNode_andExpiredLotsAreNotSellable() {
        NxInventory fresh = row(STORE_A, 20, 0, 0, 0, 0);
        NxInventory expired = row(STORE_A, 15, 0, 0, 0, 0);
        expired.setExpiryDate(TODAY.minusDays(1));
        NxInventory expiresToday = row(STORE_A, 5, 0, 0, 0, 0);
        expiresToday.setExpiryDate(TODAY);

        LocationQuantity result = at(InventoryPublishPlanner.plan("SKU-1", List.of(fresh, expired, expiresToday),
                List.of(rule(STORE_A, "111")), NODES, TODAY), "111");

        assertEquals(25, result.quantity(), "the lot expiring today is still sellable today");
        assertEquals(15, result.contributions().get(0).expiredLotUnits());
    }

    @Test
    void bufferAndCap_limitWhatANodeOffers() {
        List<NxInventory> rows = List.of(row(STORE_A, 100, 0, 0, 0, 0), row(STORE_B, 100, 0, 0, 0, 0));

        List<LocationQuantity> plan = InventoryPublishPlanner.plan("SKU-1", rows, List.of(
                new Rule(STORE_A, "900", true, 10, null),
                new Rule(STORE_B, "900", true, 0, 25)), NODES, TODAY);

        LocationQuantity result = at(plan, "900");
        assertEquals(90 + 25, result.quantity());
        Contribution b = result.contributions().stream().filter(c -> c.nodeId().equals(STORE_B)).findFirst().orElseThrow();
        assertEquals(100, b.sellable());
        assertEquals(25, b.contributed());
        assertEquals(25, b.cappedTo());
    }

    @Test
    void inactiveNodesAndDisabledMappings_contributeNothing() {
        Map<UUID, Node> nodes = Map.of(
                STORE_A, new Node(STORE_A, "Store A", false),
                STORE_B, new Node(STORE_B, "Store B", true));
        List<NxInventory> rows = List.of(row(STORE_A, 50, 0, 0, 0, 0), row(STORE_B, 50, 0, 0, 0, 0));

        List<LocationQuantity> plan = InventoryPublishPlanner.plan("SKU-1", rows, List.of(
                rule(STORE_A, "900"), new Rule(STORE_B, "900", false, 0, null)), nodes, TODAY);

        LocationQuantity result = at(plan, "900");
        assertEquals(0, result.quantity());
        assertEquals("NODE_INACTIVE", result.contributions().get(0).excluded());
        assertEquals(1, result.contributions().size(), "a disabled mapping is not even listed");
    }

    @Test
    void aMappedNodeThatNoLongerExists_isReportedNotSilentlyDropped() {
        UUID ghost = UUID.randomUUID();

        LocationQuantity result = at(InventoryPublishPlanner.plan("SKU-1", List.of(), List.of(rule(ghost, "900")), NODES, TODAY), "900");

        assertEquals(0, result.quantity());
        assertEquals("NODE_NOT_FOUND", result.contributions().get(0).excluded());
    }

    @Test
    void aSkuWithNoStockAnywhere_publishesZero_soTheChannelStopsSellingIt() {
        LocationQuantity result = at(InventoryPublishPlanner.plan("SKU-1", List.of(), List.of(rule(STORE_A, "111")), NODES, TODAY), "111");

        assertEquals(0, result.quantity());
    }

    @Test
    void noMappings_planNothing() {
        assertTrue(InventoryPublishPlanner.plan("SKU-1", List.of(row(STORE_A, 5, 0, 0, 0, 0)), List.of(), NODES, TODAY).isEmpty());
    }
}
