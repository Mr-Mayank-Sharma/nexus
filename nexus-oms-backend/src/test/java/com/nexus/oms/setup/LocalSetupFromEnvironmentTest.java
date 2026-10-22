package com.nexus.oms.setup;

import com.nexus.oms.dto.IntegrationStoreRequest;
import com.nexus.oms.dto.RegisterRequest;
import com.nexus.oms.entity.NxBigCommerceConfig;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.repository.NxBigCommerceConfigRepository;
import com.nexus.oms.repository.NxIntegrationStoreRepository;
import com.nexus.oms.service.AuthService;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.tenancy.PlatformDirectory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The .env setup: nothing without values; an admin, a Shopify store and a BigCommerce store when they are given. */
class LocalSetupFromEnvironmentTest {

    private final AuthService authService = mock(AuthService.class);
    private final PlatformDirectory platformDirectory = mock(PlatformDirectory.class);
    private final IntegrationStoreService storeService = mock(IntegrationStoreService.class);
    private final NxIntegrationStoreRepository storeRepository = mock(NxIntegrationStoreRepository.class);
    private final NxBigCommerceConfigRepository bigCommerceConfigRepository = mock(NxBigCommerceConfigRepository.class);

    private LocalSetupFromEnvironment setup(String adminUsername, String shopDomain, String storeHash) {
        return new LocalSetupFromEnvironment(authService, platformDirectory, storeService, storeRepository, bigCommerceConfigRepository,
                adminUsername, "Str0ng!Password", "admin@nexus.local", "My Company",
                shopDomain, "shpat_test", "", "",
                storeHash, "bc_token", "bc_client");
    }

    @Test
    void doesNothingWithoutAnAdminUsername() {
        setup("", "shop.myshopify.com", "abc123").setUpAtStartUp();
        verifyNoInteractions(authService, storeService, bigCommerceConfigRepository);
    }

    @Test
    void createsTheAdminAndConnectsBothStoresOnAnEmptyDatabase() {
        UUID company = UUID.randomUUID();
        when(platformDirectory.companyOfUsername("admin")).thenReturn(Optional.empty(), Optional.of(company));
        when(storeRepository.findByTenantIdAndStoreCode(company, LocalSetupFromEnvironment.SHOPIFY_STORE_CODE)).thenReturn(Optional.empty());
        when(bigCommerceConfigRepository.findByTenantId(company)).thenReturn(Optional.empty());

        setup("admin", "shop.myshopify.com", "abc123").setUpAtStartUp();

        ArgumentCaptor<RegisterRequest> admin = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(admin.capture());
        assertEquals("ADMIN", admin.getValue().getRole());
        assertEquals("My Company", admin.getValue().getCompanyName());

        ArgumentCaptor<IntegrationStoreRequest> shopify = ArgumentCaptor.forClass(IntegrationStoreRequest.class);
        verify(storeService).createStore(eq(company), shopify.capture());
        assertEquals("SHOPIFY", shopify.getValue().getPlatform());
        assertEquals("shop.myshopify.com", shopify.getValue().getSettings().get("shop_domain"));
        assertEquals("shpat_test", shopify.getValue().getSettings().get("access_token"));

        ArgumentCaptor<NxBigCommerceConfig> bigCommerce = ArgumentCaptor.forClass(NxBigCommerceConfig.class);
        verify(bigCommerceConfigRepository).save(bigCommerce.capture());
        assertEquals("abc123", bigCommerce.getValue().getStoreHash());
        assertEquals(company, bigCommerce.getValue().getTenantId());
    }

    @Test
    void anExistingAdminAndStoreAreUpdatedNotCreatedAgain() {
        UUID company = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(platformDirectory.companyOfUsername("admin")).thenReturn(Optional.of(company));
        NxIntegrationStore existing = NxIntegrationStore.builder().id(storeId).tenantId(company).storeCode("shopify").build();
        when(storeRepository.findByTenantIdAndStoreCode(company, "shopify")).thenReturn(Optional.of(existing));

        setup("admin", "shop.myshopify.com", "").setUpAtStartUp();

        verify(authService, never()).register(any());
        verify(storeService, never()).createStore(any(), any());
        verify(storeService).updateSetting(storeId, "access_token", "shpat_test");
        verifyNoInteractions(bigCommerceConfigRepository);
    }
}
