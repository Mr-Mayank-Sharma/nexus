package com.nexus.oms.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Found live: allocating to a DISTRIBUTION_CENTER or COLD_STORE failed on the node_type check constraint. */
class AllocationNodeTypeTest {

    private static final Set<String> ALLOWED = Set.of("WAREHOUSE", "DC", "CROSS_DOCK", "DROP_SHIP", "STORE");

    @Test
    void everyWarehouseTypeMapsToATypeTheTableAccepts() {
        for (String type : new String[]{null, "", "STORE", "WAREHOUSE", "DISTRIBUTION_CENTER", "COLD_STORE", "AUTOMATED_DC",
                "FULFILLMENT_CENTER", "DC", "CROSS_DOCK", "DROP_SHIP", "popup_store", "something-new"}) {
            assertTrue(ALLOWED.contains(OrderRoutingService.allocationNodeType(type)), String.valueOf(type));
        }
    }

    @Test
    void aStoreStaysAStore_andDistributionCentresAreDCs() {
        assertEquals("STORE", OrderRoutingService.allocationNodeType("STORE"));
        assertEquals("DC", OrderRoutingService.allocationNodeType("DISTRIBUTION_CENTER"));
        assertEquals("DC", OrderRoutingService.allocationNodeType("AUTOMATED_DC"));
        assertEquals("WAREHOUSE", OrderRoutingService.allocationNodeType("COLD_STORE"));
        assertEquals("WAREHOUSE", OrderRoutingService.allocationNodeType(null));
    }
}
