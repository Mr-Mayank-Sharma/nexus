package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.IntegrationStoreRequest;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxIntegrationSyncConfig;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Found live: connecting a real Shopify store switched on scheduled stock, fulfilment and refund writes. */
class NewConnectionReadOnlyTest {

    @Test
    void aNewShopifyConnectionStartsReadOnly() {
        NxIntegrationStoreRepository stores = mock(NxIntegrationStoreRepository.class);
        NxIntegrationSyncConfigRepository configs = mock(NxIntegrationSyncConfigRepository.class);
        IntegrationStoreService service = new IntegrationStoreService(stores,
                mock(NxIntegrationStoreSettingRepository.class), configs, mock(NxSyncLogRepository.class),
                new ObjectMapper(), new CredentialVault());
        when(stores.findByTenantIdAndStoreCode(any(), any())).thenReturn(Optional.empty());
        when(stores.save(any())).thenAnswer(inv -> {
            NxIntegrationStore s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        IntegrationStoreRequest r = new IntegrationStoreRequest();
        r.setStoreCode("live");
        r.setStoreName("Live");
        r.setPlatform("SHOPIFY");
        r.setSettings(Map.of("shop_domain", "x.myshopify.com"));

        service.createStore(UUID.randomUUID(), r);

        ArgumentCaptor<NxIntegrationSyncConfig> saved = ArgumentCaptor.forClass(NxIntegrationSyncConfig.class);
        verify(configs, atLeast(1)).save(saved.capture());
        Map<String, Boolean> enabled = new HashMap<>();
        saved.getAllValues().forEach(c -> enabled.put(c.getSyncType(), c.getEnabled()));
        assertEquals(Set.of("ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH", "FULFILLMENT_PUSH", "REFUND_PUSH"), enabled.keySet());
        assertTrue(enabled.get("ORDER_IMPORT"));
        assertTrue(enabled.get("PRODUCT_SYNC"));
        assertFalse(enabled.get("INVENTORY_PUSH"));
        assertFalse(enabled.get("FULFILLMENT_PUSH"));
        assertFalse(enabled.get("REFUND_PUSH"));
    }
}
