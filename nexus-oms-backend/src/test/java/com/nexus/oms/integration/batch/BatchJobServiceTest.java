package com.nexus.oms.integration.batch;

import com.nexus.oms.integration.core.Connector;
import com.nexus.oms.integration.core.ConnectorMetadata;
import com.nexus.oms.integration.core.IntegrationLog;
import com.nexus.oms.integration.core.StoreConnectorService;
import com.nexus.oms.integration.dto.SyncResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchJobServiceTest {

    private final StoreConnectorService connections = mock(StoreConnectorService.class);
    private final IntegrationLog log = mock(IntegrationLog.class);
    private final BatchJobService service = new BatchJobService(connections, log);

    private Connector connectorAnswering(SyncResult result) {
        Connector c = mock(Connector.class);
        ConnectorMetadata md = ConnectorMetadata.builder().name("Magento").platformType("MAGENTO").build();
        when(c.getMetadata()).thenReturn(md);
        when(c.executeSync(any(), any(), any())).thenReturn(result);
        when(connections.open("c1")).thenReturn(c);
        return c;
    }

    @Test
    void aPreviewKeepsItsExplanation_soTheScreenCanSayNothingWasImported() {
        connectorAnswering(SyncResult.builder().syncType("ORDER_IMPORT").status(SyncResult.Status.NOT_SUPPORTED)
                .addWarning("Magento returned 3 orders, but this connector only reads them; nothing was imported into Nexus").build());

        var job = service.submitJob("c1", "ORDER_IMPORT", UUID.randomUUID(), Map.of());

        assertEquals("NOT_SUPPORTED", job.getStatus());
        assertEquals(0, job.getItemsSucceeded());
        assertTrue(job.getMessage().contains("nothing was imported"));
    }

    @Test
    void aFailureCarriesTheReason() {
        connectorAnswering(SyncResult.builder().syncType("REFUND_PUSH").status(SyncResult.Status.FAILED).addError("charge_id required").build());

        var job = service.submitJob("c1", "REFUND_PUSH", UUID.randomUUID(), Map.of());

        assertEquals("FAILED", job.getStatus());
        assertEquals("charge_id required", job.getMessage());
    }

    @Test
    void aSyncIsLoggedOnce() {
        connectorAnswering(SyncResult.builder().syncType("ORDER_IMPORT").status(SyncResult.Status.NOT_SUPPORTED).build());
        service.submitJob("c1", "ORDER_IMPORT", UUID.randomUUID(), Map.of());
        verify(log, times(1)).startSync(any(), any(), any());
    }
}
