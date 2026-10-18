package com.nexus.oms.service;

import com.nexus.oms.repository.OrderAllocationRepository;
import com.nexus.oms.repository.OrderRejectionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PickFailureServiceTest {

    @Test
    void aStoreWithNoHistorySitsAtThePrior() {
        assertEquals(PickFailureService.PRIOR_RATE, PickFailureService.smoothed(0, 0), 1e-9);
    }

    @Test
    void twoRejectionsInTenUnitsIsNotTreatedAsTwentyPercent() {
        // (2 + 0.05*20) / (10 + 20) = 0.1: small samples are pulled towards the prior.
        assertEquals(0.1, PickFailureService.smoothed(2, 10), 1e-9);
    }

    @Test
    void aLongRecordMovesTheRateToTheStoresOwn() {
        assertEquals(1.0 / 201.0 * 1.0 + 0.0, PickFailureService.smoothed(0, 181), 0.01);
        assertTrue(PickFailureService.smoothed(300, 1000) > 0.28 && PickFailureService.smoothed(300, 1000) < 0.31);
    }

    @Test
    void theRateIsNeverAboveOne_evenOnOddData() {
        assertEquals(1.0, PickFailureService.smoothed(500, 5), 1e-9);
        assertEquals(PickFailureService.PRIOR_RATE, PickFailureService.smoothed(-3, -4), 1e-9);
    }

    @Test
    void ratesAreBuiltFromAllocationsAndRejections_perStore() {
        OrderAllocationRepository allocations = mock(OrderAllocationRepository.class);
        OrderRejectionRepository rejections = mock(OrderRejectionRepository.class);
        UUID tenant = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        when(allocations.allocatedUnitsByNode(any(), any())).thenReturn(List.of(new Object[]{a, 100L}, new Object[]{b, 40L}));
        when(rejections.rejectedUnitsByNode(any(), any())).thenReturn(List.<Object[]>of(new Object[]{a, 20L}));

        var rates = new PickFailureService(allocations, rejections).ratesFor(tenant);

        assertEquals(100, rates.get(a).allocatedUnits());
        assertEquals(20, rates.get(a).rejectedUnits());
        assertEquals((20 + 1.0) / 120.0, rates.get(a).rate(), 1e-9);
        assertEquals(0, rates.get(b).rejectedUnits());
        assertTrue(rates.get(b).rate() < rates.get(a).rate());
        assertEquals(PickFailureService.PRIOR_RATE, PickFailureService.rateOrPrior(rates, UUID.randomUUID()), 1e-9);
    }
}
