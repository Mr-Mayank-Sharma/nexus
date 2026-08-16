package com.nexus.oms.integration.connector.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.integration.core.DataMapper;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuickBooksConnectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private RestProtocolAdapter restClient;
    @Mock private GraphqlProtocolAdapter graphqlClient;
    @Mock private DataMapper dataMapper;

    private CredentialVault credentialVault;
    private QuickBooksConnector connector;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        credentialVault = new CredentialVault();
        connector = new QuickBooksConnector(credentialVault, restClient, graphqlClient, dataMapper);
        tenantId = UUID.randomUUID();

        ConnectorConfig config = new ConnectorConfig();
        config.putSetting("realm_id", "12345");
        config.putSetting("company_id", "12345");
        connector.initialize(config);
    }

    @Test
    void testConnection_companyInfoPresent_returnsTrue() throws Exception {
        JsonNode companyInfo = MAPPER.readTree("{\"CompanyInfo\": {\"CompanyName\": \"Acme\"}}");
        when(restClient.get(anyString(), anyString(), anyMap(), anyMap())).thenReturn(companyInfo);

        assertTrue(connector.testConnection());
    }

    @Test
    void pushRefunds_pushesCreditMemoAndCapturesIdempotencyKey() throws Exception {
        JsonNode response = MAPPER.readTree("{\"CreditMemo\": {\"Id\": \"999\"}}");
        when(restClient.post(anyString(), anyString(), anyMap(), any())).thenReturn(response);

        SyncResult result = connector.pushRefunds(tenantId, Map.of("refunds", List.of(
                Map.of("refundId", "R-1", "amount", 25.00, "customerRef", "CUST-1", "reason", "damaged"))));

        assertEquals(SyncResult.Status.COMPLETED, result.getStatus());
        assertEquals(1, result.getItemsSucceeded());
        assertEquals(0, result.getItemsFailed());

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(restClient).post(anyString(), eq("/v3/company/12345/creditmemo"),
                headers.capture(), any());
        assertTrue(headers.getValue().containsKey("Idempotency-Key"));
        assertEquals("nexus-refund-R-1", headers.getValue().get("Idempotency-Key"));
    }

    @Test
    void pushRefunds_duplicateRefundId_isSkipped() throws Exception {
        JsonNode response = MAPPER.readTree("{\"CreditMemo\": {\"Id\": \"999\"}}");
        when(restClient.post(anyString(), anyString(), anyMap(), any())).thenReturn(response);

        Map<String, Object> params = Map.of("refunds", List.of(
                Map.of("refundId", "R-1", "amount", 25.00),
                Map.of("refundId", "R-1", "amount", 25.00)));

        SyncResult result = connector.pushRefunds(tenantId, params);
        SyncResult second = connector.pushRefunds(tenantId, params);

        assertEquals(1, result.getItemsSucceeded());
        assertEquals(1, result.getItemsSkipped());
        assertEquals(0, second.getItemsSucceeded());
        assertEquals(2, second.getItemsSkipped());
        verify(restClient, times(1)).post(anyString(), anyString(), anyMap(), any());
    }

    @Test
    void pushRefunds_failureReportsPartialAndAllowsRetry() throws Exception {
        when(restClient.post(anyString(), anyString(), anyMap(), any()))
                .thenThrow(new RuntimeException("network"))
                .thenReturn(MAPPER.readTree("{\"CreditMemo\": {\"Id\": \"999\"}}"));

        Map<String, Object> params = Map.of("refunds", List.of(
                Map.of("refundId", "R-2", "amount", 10.00)));

        SyncResult first = connector.pushRefunds(tenantId, params);
        SyncResult retry = connector.pushRefunds(tenantId, params);

        assertEquals(SyncResult.Status.PARTIAL, first.getStatus());
        assertEquals(1, first.getItemsFailed());
        assertFalse(first.getErrors().isEmpty());
        assertEquals(1, retry.getItemsSucceeded());
        assertEquals(0, retry.getItemsFailed());
    }
}
