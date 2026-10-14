package com.nexus.oms.integration.connector;

import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.integration.core.DataMapper;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A generic connector can read but cannot write. Its writes used to answer COMPLETED with zero items,
 * so the marketplace and the schedulers believed inventory, fulfilments and refunds had synced.
 */
class GenericConnectorHonestyTest {

    private final GenericHttpConnector ups = new GenericHttpConnector(mock(CredentialVault.class), mock(RestProtocolAdapter.class),
            mock(GraphqlProtocolAdapter.class), mock(DataMapper.class), "UPS", "UPS", "UPS", "SHIPPING", "carrier", "https://example.test");

    @Test
    void unimplementedPushes_areNotSupported_notCompleted() {
        for (SyncResult r : new SyncResult[]{
                ups.pushInventory(UUID.randomUUID(), Map.of()),
                ups.pushFulfillments(UUID.randomUUID(), Map.of()),
                ups.pushRefunds(UUID.randomUUID(), Map.of())}) {
            assertEquals(SyncResult.Status.NOT_SUPPORTED, r.getStatus(), r.getSyncType());
            assertEquals(0, r.getItemsSucceeded());
            assertTrue(r.getWarnings().get(0).contains("nothing was sent"));
        }
    }

    @Test
    void theSyncTypeIsReported_soTheCallerKnowsWhichOperationWasRefused() {
        assertEquals("INVENTORY_PUSH", ups.pushInventory(UUID.randomUUID(), Map.of()).getSyncType());
        assertEquals("REFUND_PUSH", ups.pushRefunds(UUID.randomUUID(), Map.of()).getSyncType());
    }

    @Test
    void pullingRecordsThatAreNotImported_isNotReportedAsAnImport() throws Exception {
        RestProtocolAdapter rest = mock(RestProtocolAdapter.class);
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{}");
        org.mockito.Mockito.when(rest.paginatedGet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.List.of(node, node, node));
        GenericHttpConnector walmart = new GenericHttpConnector(mock(CredentialVault.class), rest, mock(GraphqlProtocolAdapter.class),
                mock(DataMapper.class), "WALMART", "Walmart", "Walmart", "ECOMMERCE", "marketplace", "https://example.test");

        SyncResult r = walmart.syncOrders(UUID.randomUUID(), Map.of());

        assertEquals(SyncResult.Status.NOT_SUPPORTED, r.getStatus());
        assertEquals(0, r.getItemsSucceeded(), "three downloaded records are not three imported orders");
        assertTrue(r.getWarnings().get(0).contains("3 order(s)"));
    }
}
