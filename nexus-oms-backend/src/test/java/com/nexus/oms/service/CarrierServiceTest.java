package com.nexus.oms.service;

import com.nexus.oms.entity.NxCarrier;
import com.nexus.oms.repository.CarrierRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarrierServiceTest {

    @Mock
    private CarrierRepository carrierRepository;

    private CarrierService carrierService;
    private UUID tenantId;
    private UUID carrierId;

    @BeforeEach
    void setUp() {
        carrierService = new CarrierService(carrierRepository);
        tenantId = UUID.randomUUID();
        carrierId = UUID.randomUUID();
    }

    @Test
    void getCarriers() {
        Page<NxCarrier> page = new PageImpl<>(List.of(new NxCarrier()));
        when(carrierRepository.findByTenantId(tenantId, Pageable.unpaged())).thenReturn(page);

        assertSame(page, carrierService.getCarriers(tenantId, Pageable.unpaged()));
    }

    @Test
    void getCarrier_found() {
        NxCarrier carrier = new NxCarrier();
        carrier.setId(carrierId);
        when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));

        assertSame(carrier, carrierService.getCarrier(carrierId));
    }

    @Test
    void getCarrier_notFound_throws() {
        when(carrierRepository.findById(carrierId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> carrierService.getCarrier(carrierId));
    }

    @Test
    void createCarrier() {
        NxCarrier input = new NxCarrier();
        input.setName("Test Carrier");
        input.setCode("TST");

        when(carrierRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            NxCarrier result = carrierService.createCarrier(input);

            assertEquals(tenantId, result.getTenantId());
            assertEquals("Test Carrier", result.getName());
            verify(carrierRepository).save(input);
        }
    }

    @Test
    void updateCarrier() {
        NxCarrier existing = new NxCarrier();
        existing.setId(carrierId);
        existing.setName("Old Name");

        NxCarrier updates = new NxCarrier();
        updates.setName("New Name");
        updates.setCode("NEW");

        when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(existing));
        when(carrierRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxCarrier result = carrierService.updateCarrier(carrierId, updates);

        assertEquals("New Name", result.getName());
        assertEquals("NEW", result.getCode());
    }

    @Test
    void deleteCarrier() {
        NxCarrier carrier = new NxCarrier();
        carrier.setId(carrierId);
        when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));

        carrierService.deleteCarrier(carrierId);

        verify(carrierRepository).delete(carrier);
    }

    @Test
    void getCarrierKPIs() {
        NxCarrier c1 = new NxCarrier();
        c1.setIsActive(true);
        c1.setTotalShipments(100L);
        c1.setOtdRate(new BigDecimal("95.0"));
        c1.setAvgCost(new BigDecimal("25.50"));

        NxCarrier c2 = new NxCarrier();
        c2.setIsActive(false);
        c2.setTotalShipments(50L);
        c2.setOtdRate(new BigDecimal("85.0"));
        c2.setAvgCost(new BigDecimal("30.00"));

        when(carrierRepository.findByTenantId(tenantId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(c1, c2)));

        Map<String, Object> kpis = carrierService.getCarrierKPIs(tenantId);

        assertEquals(1L, kpis.get("activeCarriers"));
        assertEquals(2, kpis.get("totalCarriers"));
        assertEquals(150L, kpis.get("totalShipments"));
        assertEquals(new BigDecimal("90.00"), kpis.get("avgOtdRate"));
        assertEquals(new BigDecimal("27.75"), kpis.get("avgCost"));
    }
}
