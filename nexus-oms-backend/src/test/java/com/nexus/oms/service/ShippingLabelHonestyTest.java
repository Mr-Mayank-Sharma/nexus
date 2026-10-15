package com.nexus.oms.service;

import com.nexus.oms.entity.NxShippingLabel;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.kafka.KafkaProducerService;
import com.nexus.oms.repository.ShippingLabelRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShippingLabelHonestyTest {

    private final ShippingLabelRepository repo = mock(ShippingLabelRepository.class);
    private final ShippingLabelService service = new ShippingLabelService(repo, mock(CarrierLabelConfigService.class), mock(KafkaProducerService.class));

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(UUID.randomUUID());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private NxShippingLabel label(String carrier, String tracking) {
        NxShippingLabel l = new NxShippingLabel();
        l.setCarrier(carrier);
        l.setTrackingNumber(tracking);
        return l;
    }

    @Test
    void localDemo_inventsATrackingNumber_thatSaysSoOnItsFace() {
        NxShippingLabel l = service.generateLabel(label("UPS", null));
        assertTrue(l.getTrackingNumber().startsWith("SIM-"));
        assertEquals("SIMULATED", l.getLabelSource());
    }

    @Test
    void production_refusesToInventATrackingNumber() {
        ReflectionTestUtils.setField(service, "simulationEnabled", false);
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.generateLabel(label("UPS", null)));
        assertTrue(e.getMessage().contains("will not invent"));
        verify(repo, never()).save(any());
    }

    @Test
    void aTrackingNumberTypedInFromTheCarrier_isKept_andMarkedManual() {
        ReflectionTestUtils.setField(service, "simulationEnabled", false);
        NxShippingLabel l = service.generateLabel(label("UPS", "1Z999AA10123456784"));
        assertEquals("1Z999AA10123456784", l.getTrackingNumber());
        assertEquals("MANUAL", l.getLabelSource());
    }

    @Test
    void pickupReferencesAreInternal_neverPassedOffAsCarrierNumbers() {
        NxShippingLabel l = service.generateBopisLabel(UUID.randomUUID(), "SO-1", "Store", "1 Main St");
        assertTrue(l.getTrackingNumber().startsWith("PICKUP-"));
        assertEquals("INTERNAL", l.getLabelSource());
    }
}
