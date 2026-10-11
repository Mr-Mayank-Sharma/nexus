package com.nexus.oms.service.shopify;

import com.nexus.oms.entity.NxChannelLocationMapping;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.NxChannelLocationMappingRepository;
import com.nexus.oms.repository.WarehouseRepository;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.shopify.InventoryChannelMappingService.MappingInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryChannelMappingServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID nodeA = UUID.randomUUID();
    private final UUID nodeB = UUID.randomUUID();
    private final NxChannelLocationMappingRepository mappings = mock(NxChannelLocationMappingRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final IntegrationStoreService stores = mock(IntegrationStoreService.class);
    private InventoryChannelMappingService service;

    @BeforeEach
    void setUp() {
        service = new InventoryChannelMappingService(mappings, warehouses, stores, mock(ShopifyClient.class), mock(ShopifyTokenService.class));
        when(stores.getStore(storeId)).thenReturn(NxIntegrationStore.builder().id(storeId).tenantId(tenant).build());
        when(warehouses.findAllById(any())).thenReturn(List.of(wh(nodeA, tenant), wh(nodeB, tenant)));
        when(mappings.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Warehouse wh(UUID id, UUID tenant) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setTenantId(tenant);
        return w;
    }

    private static MappingInput in(UUID node, String location) {
        return new MappingInput(node, location, "Loc " + location, null, null, null);
    }

    @Test
    void replacesTheStoresMappings_withDefaults() {
        List<NxChannelLocationMapping> saved = service.replace(storeId, List.of(in(nodeA, "111"), in(nodeB, "111")));

        assertEquals(2, saved.size());
        assertTrue(saved.get(0).getEnabled());
        assertEquals(0, saved.get(0).getBufferUnits());
        assertNull(saved.get(0).getContributionCap());
        assertEquals(tenant, saved.get(0).getTenantId());
        verify(mappings).deleteByIntegrationStoreId(storeId);
    }

    @Test
    void rejectsANodeMappedToTwoLocations() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.replace(storeId, List.of(in(nodeA, "111"), in(nodeA, "222"))));

        assertTrue(e.getMessage().contains("only one Shopify location"));
        verify(mappings, never()).deleteByIntegrationStoreId(any());
    }

    @Test
    void rejectsANodeFromAnotherTenant() {
        when(warehouses.findAllById(any())).thenReturn(List.of(wh(nodeA, UUID.randomUUID())));

        assertThrows(BadRequestException.class, () -> service.replace(storeId, List.of(in(nodeA, "111"))));
    }

    @Test
    void rejectsNonNumericLocationIdsAndNegativeLimits() {
        assertThrows(BadRequestException.class, () -> service.replace(storeId, List.of(in(nodeA, "gid://shopify/Location/1"))));
        assertThrows(BadRequestException.class, () -> service.replace(storeId,
                List.of(new MappingInput(nodeA, "111", null, true, -1, null))));
        assertThrows(BadRequestException.class, () -> service.replace(storeId,
                List.of(new MappingInput(nodeA, "111", null, true, 0, -5))));
        assertThrows(BadRequestException.class, () -> service.replace(storeId, List.of(in(null, "111"))));
    }

    @Test
    void anExplicitlyDisabledMapping_staysDisabled() {
        ArgumentCaptor<List<NxChannelLocationMapping>> captor = ArgumentCaptor.forClass(List.class);

        service.replace(storeId, List.of(new MappingInput(nodeA, "111", null, false, 5, 20)));

        verify(mappings).saveAll(captor.capture());
        assertFalse(captor.getValue().get(0).getEnabled());
        assertEquals(5, captor.getValue().get(0).getBufferUnits());
        assertEquals(20, captor.getValue().get(0).getContributionCap());
    }
}
