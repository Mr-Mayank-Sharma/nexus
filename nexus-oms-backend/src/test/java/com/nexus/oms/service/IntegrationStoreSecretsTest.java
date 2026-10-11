package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.IntegrationStoreRequest;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxIntegrationStoreSetting;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntegrationStoreSecretsTest {

    private final NxIntegrationStoreRepository stores = mock(NxIntegrationStoreRepository.class);
    private final NxIntegrationStoreSettingRepository settings = mock(NxIntegrationStoreSettingRepository.class);
    private final CredentialVault vault = new CredentialVault();
    private IntegrationStoreService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new IntegrationStoreService(stores, settings, mock(NxIntegrationSyncConfigRepository.class),
                mock(NxSyncLogRepository.class), new ObjectMapper(), vault);
        when(stores.findByTenantIdAndStoreCode(any(), any())).thenReturn(Optional.empty());
        when(stores.save(any())).thenAnswer(inv -> {
            NxIntegrationStore s = inv.getArgument(0);
            s.setId(storeId);
            return s;
        });
    }

    private IntegrationStoreRequest request(Map<String, String> values) {
        IntegrationStoreRequest r = new IntegrationStoreRequest();
        r.setStoreCode("acme");
        r.setStoreName("Acme");
        r.setPlatform("shopify");
        r.setSettings(values);
        return r;
    }

    @Test
    void secretsAreEncryptedAtRest_plainSettingsAreNot() {
        service.createStore(tenant, request(Map.of("access_token", "shpat_123", "shop_domain", "acme.myshopify.com")));

        ArgumentCaptor<NxIntegrationStoreSetting> saved = ArgumentCaptor.forClass(NxIntegrationStoreSetting.class);
        verify(settings, times(2)).save(saved.capture());
        for (NxIntegrationStoreSetting s : saved.getAllValues()) {
            if (s.getSettingType().equals("access_token")) {
                assertTrue(s.getSettingValue().startsWith("enc:v1:"));
                assertFalse(s.getSettingValue().contains("shpat_123"));
                assertTrue(s.getIsEncrypted());
            } else {
                assertEquals("acme.myshopify.com", s.getSettingValue());
                assertFalse(s.getIsEncrypted());
            }
        }
    }

    @Test
    void getSetting_decryptsSealedValues_andPassesLegacyPlaintextThrough() {
        String sealed = "enc:v1:" + vault.encrypt("shpat_123");
        when(settings.findByStoreIdAndSettingType(storeId, "access_token"))
                .thenReturn(Optional.of(NxIntegrationStoreSetting.builder().settingType("access_token").settingValue(sealed).build()));
        when(settings.findByStoreIdAndSettingType(storeId, "client_secret"))
                .thenReturn(Optional.of(NxIntegrationStoreSetting.builder().settingType("client_secret").settingValue("legacy").build()));

        assertEquals("shpat_123", service.getSetting(storeId, "access_token"));
        assertEquals("legacy", service.getSetting(storeId, "client_secret"));
    }

    @Test
    void apiView_masksSecrets_butShowsOtherSettings() {
        when(settings.findByStoreId(storeId)).thenReturn(List.of(
                NxIntegrationStoreSetting.builder().settingType("access_token").settingValue("enc:v1:xyz").isEncrypted(true).build(),
                NxIntegrationStoreSetting.builder().settingType("client_secret").settingValue("").isEncrypted(true).build(),
                NxIntegrationStoreSetting.builder().settingType("shop_domain").settingValue("acme.myshopify.com").isEncrypted(false).build()));

        Map<String, String> byType = new HashMap<>();
        service.getSettings(storeId).forEach(s -> byType.put(s.getSettingType(), s.getSettingValue()));

        assertEquals(IntegrationStoreService.MASK, byType.get("access_token"));
        assertEquals("", byType.get("client_secret"), "an unset secret is not shown as set");
        assertEquals("acme.myshopify.com", byType.get("shop_domain"));
    }

    @Test
    void updateStore_ignoresAMaskEchoedBackByTheUi() {
        NxIntegrationStore store = NxIntegrationStore.builder().id(storeId).tenantId(tenant).storeCode("acme").build();
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        NxIntegrationStoreSetting existing = NxIntegrationStoreSetting.builder().storeId(storeId)
                .settingType("access_token").settingValue("enc:v1:original").build();
        when(settings.findByStoreIdAndSettingType(storeId, "access_token")).thenReturn(Optional.of(existing));
        IntegrationStoreRequest r = request(Map.of("access_token", IntegrationStoreService.MASK));

        service.updateStore(storeId, r);

        verify(settings, never()).save(any());
        assertEquals("enc:v1:original", existing.getSettingValue());
    }

    @Test
    void sealLegacySecrets_encryptsPlaintextOnce() {
        NxIntegrationStoreSetting plain = NxIntegrationStoreSetting.builder().settingType("client_secret").settingValue("s3cret").build();
        NxIntegrationStoreSetting sealed = NxIntegrationStoreSetting.builder().settingType("access_token")
                .settingValue("enc:v1:" + vault.encrypt("tok")).build();
        NxIntegrationStoreSetting other = NxIntegrationStoreSetting.builder().settingType("shop_domain").settingValue("a.b").build();
        when(settings.findAll()).thenReturn(List.of(plain, sealed, other));

        assertEquals(1, service.sealLegacySecrets());

        assertTrue(plain.getSettingValue().startsWith("enc:v1:"));
        assertEquals("a.b", other.getSettingValue());
        assertEquals(0, service.sealLegacySecrets(), "running again changes nothing");
    }

    @Test
    void webhookSecret_isDecryptedForTheShopDomain() {
        NxIntegrationStore store = NxIntegrationStore.builder().id(storeId).build();
        when(stores.findByExternalDomain("acme.myshopify.com")).thenReturn(Optional.of(store));
        when(settings.findByStoreIdAndSettingType(storeId, "client_secret")).thenReturn(Optional.of(
                NxIntegrationStoreSetting.builder().settingValue("enc:v1:" + vault.encrypt("app-secret")).build()));

        assertEquals(Optional.of("app-secret"), service.findWebhookSecretForDomain("acme.myshopify.com"));
        assertEquals(Optional.empty(), service.findWebhookSecretForDomain("unknown.myshopify.com"));
    }
}
