package com.nexus.oms.integration.core;

import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.repository.NxIntegrationStoreRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StoreConnectorServiceTest {

    private final ConnectorRegistry registry = mock(ConnectorRegistry.class);
    private final IntegrationStoreService stores = mock(IntegrationStoreService.class);
    private final NxIntegrationStoreRepository repo = mock(NxIntegrationStoreRepository.class);
    private final StoreConnectorService service = new StoreConnectorService(registry, stores, repo, mock(CredentialVault.class));
    private final UUID mine = UUID.randomUUID();
    private final UUID theirs = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(mine);
        when(registry.getRegisteredPlatforms()).thenReturn(Set.of("SHOPIFY", "STRIPE"));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private NxIntegrationStore store(UUID tenant, String platform) {
        return NxIntegrationStore.builder().id(UUID.randomUUID()).tenantId(tenant).platform(platform).storeName(platform).storeCode(platform).build();
    }

    @Test
    void listsOnlyThisOrganisationsConnections_ofPlatformsTheHubHasAConnectorFor() {
        var a = store(mine, "SHOPIFY");
        var unknown = store(mine, "WOOCOMMERCE");
        when(repo.findByTenantId(mine)).thenReturn(List.of(a, unknown));

        assertEquals(List.of(a), service.list());
        verify(repo, never()).findByTenantId(theirs);
    }

    @Test
    void anotherOrganisationsConnection_looksLikeItDoesNotExist() {
        var foreign = store(theirs, "SHOPIFY");
        when(repo.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class, () -> service.find(foreign.getId().toString()));
        assertThrows(ResourceNotFoundException.class, () -> service.open(foreign.getId().toString()));
        assertThrows(ResourceNotFoundException.class, () -> service.remove(foreign.getId().toString()));
        verify(stores, never()).deleteStore(any());
    }

    @Test
    void anIdThatIsNotAUuid_isNotFound_notAServerError() {
        assertThrows(ResourceNotFoundException.class, () -> service.find("not-a-uuid"));
    }

    @Test
    void opening_buildsAConnectorFromTheStoredSettings_withSecretsOpenedInMemoryOnly() {
        var s = store(mine, "STRIPE");
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));
        when(stores.getSettingsMap(s.getId())).thenReturn(Map.of("base_url", "https://api.stripe.com", "api_key", "sk_live_x"));
        Connector built = mock(Connector.class);
        when(registry.initialize(any())).thenReturn(built);

        assertSame(built, service.open(s.getId().toString()));

        ArgumentCaptor<ConnectorConfig> cfg = ArgumentCaptor.forClass(ConnectorConfig.class);
        verify(registry).initialize(cfg.capture());
        assertEquals("STRIPE", cfg.getValue().getPlatformType());
        assertEquals(mine.toString(), cfg.getValue().getTenantId());
        assertEquals("sk_live_x", cfg.getValue().getCredential("api_key"));
        assertEquals("https://api.stripe.com", cfg.getValue().getSetting("base_url"));
    }

    @Test
    void creating_savesAStoreWithSealedSettings_andRejectsUnknownPlatforms() {
        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setPlatformType("woocommerce");
        assertThrows(BadRequestException.class, () -> service.create(cfg));

        cfg.setPlatformType("stripe");
        cfg.setSettings(new HashMap<>(Map.of("base_url", "https://api.stripe.com")));
        cfg.setCredentials(new HashMap<>(Map.of("api_key", "sk_live_x")));
        service.create(cfg);

        verify(stores).createStore(eq(mine), argThat(r ->
                "STRIPE".equals(r.getPlatform()) && "sk_live_x".equals(r.getSettings().get("api_key"))
                        && "https://api.stripe.com".equals(r.getSettings().get("base_url"))));
    }

    @Test
    void theLastTestOutcomeSurvivesInTheStore_andOnlyThatOutcomeIsExposed() {
        var s = store(mine, "SHOPIFY");
        s.setMetadata("{\"somethingElse\":\"keep\"}");

        service.recordTest(s, false, "401 from the platform");

        assertTrue(s.getMetadata().contains("somethingElse"));
        Map<String, Object> last = service.lastTest(s);
        assertEquals(false, last.get("lastTestOk"));
        assertEquals("401 from the platform", last.get("lastTestMessage"));
        assertFalse(last.containsKey("somethingElse"));
        assertTrue(service.lastTest(store(mine, "SHOPIFY")).isEmpty(), "never tested means no outcome, not a green light");
    }
}
