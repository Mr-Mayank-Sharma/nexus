package com.nexus.oms.service;

import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    private ShipmentService shipmentService;
    private UUID tenantId;
    private NxShipment testShipment;

    @BeforeEach
    void setUp() {
        shipmentService = new ShipmentService(shipmentRepository);
        tenantId = UUID.randomUUID();
        testShipment = NxShipment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .orderId(UUID.randomUUID())
                .carrierId("FEDEX")
                .trackingNumber("FX-123456")
                .status("PENDING")
                .voided(false)
                .build();
    }

    @Test
    void testGetShipmentsByTenant_ReturnsList() {
        when(shipmentRepository.findByTenantId(tenantId)).thenReturn(List.of(testShipment));
        List<NxShipment> result = shipmentService.getShipmentsByTenant(tenantId);
        assertEquals(1, result.size());
        assertEquals("FX-123456", result.get(0).getTrackingNumber());
        verify(shipmentRepository).findByTenantId(tenantId);
    }

    @Test
    void testGetShipment_WhenExists_ReturnsShipment() {
        when(shipmentRepository.findById(testShipment.getId())).thenReturn(Optional.of(testShipment));
        NxShipment result = shipmentService.getShipment(testShipment.getId());
        assertEquals(testShipment.getId(), result.getId());
    }

    @Test
    void testGetShipment_WhenNotExists_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(shipmentRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> shipmentService.getShipment(id));
    }

    @Test
    void testGetByTracking_WhenExists_ReturnsShipment() {
        when(shipmentRepository.findByTrackingNumber("FX-123456")).thenReturn(Optional.of(testShipment));
        NxShipment result = shipmentService.getByTracking("FX-123456");
        assertEquals(testShipment.getId(), result.getId());
    }

    @Test
    void testGetByTracking_WhenNotExists_ThrowsException() {
        when(shipmentRepository.findByTrackingNumber("INVALID")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> shipmentService.getByTracking("INVALID"));
    }

    @Test
    void testVoidShipment_SetsVoidedAndStatus() {
        when(shipmentRepository.findById(testShipment.getId())).thenReturn(Optional.of(testShipment));
        when(shipmentRepository.save(any(NxShipment.class))).thenAnswer(i -> i.getArgument(0));

        NxShipment result = shipmentService.voidShipment(testShipment.getId());

        assertTrue(result.getVoided());
        assertEquals("VOIDED", result.getStatus());
        verify(shipmentRepository).save(testShipment);
    }
}
