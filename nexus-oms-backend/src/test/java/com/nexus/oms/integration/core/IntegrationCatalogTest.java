package com.nexus.oms.integration.core;

import com.nexus.oms.integration.core.IntegrationCatalog.Entry;
import com.nexus.oms.integration.core.IntegrationCatalog.Evidence;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import com.nexus.oms.scheduler.SyncSchedulerService;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** The catalog is the screen's source of truth, so it must agree with the code it describes. */
class IntegrationCatalogTest {

    private Map<String, ConnectorMetadata> registered() {
        ConnectorRegistry registry = new ConnectorRegistry(mock(CredentialVault.class));
        new ConnectorFactoryConfiguration(registry, mock(CredentialVault.class), mock(RestProtocolAdapter.class),
                mock(GraphqlProtocolAdapter.class), new DataMapper()).registerFactories();
        return registry.getAvailablePlatforms().stream().collect(Collectors.toMap(ConnectorMetadata::getPlatformType, m -> m));
    }

    private Map<String, Entry> byKey() {
        return IntegrationCatalog.entries().stream().collect(Collectors.toMap(Entry::key, e -> e));
    }

    @Test
    void everyPlatformTheHubOffersIsDescribed_andNothingIsDescribedThatDoesNotExist() {
        Map<String, Entry> catalog = byKey();
        for (String platform : registered().keySet()) assertTrue(catalog.containsKey(platform), platform + " is offered but not in the catalog");
        for (Entry e : IntegrationCatalog.entries()) {
            if (e.tier().equals("NATIVE") && !registered().containsKey(e.key())) continue;   // built into Nexus, not a hub platform
            assertTrue(registered().containsKey(e.key()), e.key() + " is catalogued as a hub " + e.tier() + " but the hub has no such platform");
        }
    }

    @Test
    void genericEntriesAreGenericInTheCode_andConnectorEntriesAreNot() {
        Map<String, ConnectorMetadata> reg = registered();
        for (Entry e : IntegrationCatalog.entries()) {
            ConnectorMetadata m = reg.get(e.key());
            if (m == null) continue;
            boolean writes = !m.getSupportedSyncTypes().isEmpty();
            if (e.tier().equals("GENERIC")) assertFalse(writes, e.key() + " is catalogued generic but its connector lists operations");
            if (writes) assertTrue(e.capabilities().stream().anyMatch(c -> c.evidence() != Evidence.NOT_IMPLEMENTED && !c.name().equals("Connection test")),
                    e.key() + " lists operations in code but the catalog shows none working");
        }
    }

    @Test
    void whatIsScheduledIsWhatTheCatalogCallsNative() {
        for (String p : SyncSchedulerService.SCHEDULED_PLATFORMS) {
            Entry e = byKey().get(p);
            assertNotNull(e, p);
            assertEquals("NATIVE", e.tier(), p + " has scheduled syncs, so it is built into Nexus");
        }
    }

    @Test
    void nothingIsClaimedVerifiedLiveWithoutRecordedEvidence() {
        for (Entry e : IntegrationCatalog.entries())
            for (var c : e.capabilities())
                if (c.liveVerified())
                    assertTrue(IntegrationCatalog.LIVE_EVIDENCE.containsKey(e.key() + "/" + c.name()), e.key() + "/" + c.name() + " claims a live run with no evidence");
    }

    @Test
    void entriesAreWellFormed() {
        Set<String> keys = new HashSet<>();
        for (Entry e : IntegrationCatalog.entries()) {
            assertTrue(keys.add(e.key()), "duplicate " + e.key());
            assertTrue(IntegrationCatalog.GROUP_ORDER.contains(e.group()), e.key() + " has unknown group " + e.group());
            assertFalse(e.capabilities().isEmpty(), e.key());
            assertFalse(e.summary().isBlank(), e.key());
            assertTrue(Set.of("NATIVE", "CONNECTOR", "GENERIC").contains(e.tier()), e.key());
        }
    }

    @Test
    void aSummaryNeverPromisesWhatEveryCapabilityDenies() {
        for (Entry e : IntegrationCatalog.entries())
            if (!e.hasAnythingWorking()) fail(e.key() + " has no working capability at all; remove it from the catalog or build something");
    }
}
