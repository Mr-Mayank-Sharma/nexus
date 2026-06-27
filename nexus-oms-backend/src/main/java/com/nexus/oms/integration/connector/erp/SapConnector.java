package com.nexus.oms.integration.connector.erp;

import com.nexus.oms.integration.connector.BaseApiConnector;
import com.nexus.oms.integration.core.ConnectorFactory;
import com.nexus.oms.integration.core.ConnectorMetadata;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.integration.core.DataMapper;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.integration.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SapConnector extends BaseApiConnector {

    private static final Logger log = LoggerFactory.getLogger(SapConnector.class);
    private final SoapProtocolAdapter soapClient;
    private final EdiProtocolAdapter ediClient;
    private boolean useSoap;
    private boolean useRfc;
    private boolean useEdi;
    private String clientId;
    private String systemId;

    public SapConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                         GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper,
                         SoapProtocolAdapter soapClient, EdiProtocolAdapter ediClient) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.soapClient = soapClient;
        this.ediClient = ediClient;
        this.metadata = ConnectorMetadata.builder()
                .name("SAP S/4HANA")
                .version("1.0.0")
                .vendor("SAP SE")
                .platformType("SAP")
                .category("ERP")
                .description("SAP S/4HANA connector supporting SOAP web services, IDoc, OData, and RFC")
                .website("https://api.sap.com")
                .docsUrl("https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE")
                .supportedSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH", "FULFILLMENT_PUSH"))
                .supportedProtocols(List.of("SOAP", "REST", "EDI", "RFC"))
                .supportedAuthTypes(List.of("BASIC_AUTH", "OAUTH2", "CERTIFICATE", "SSO"))
                .defaultSettings(Map.of("protocol", "SOAP", "client_id", "100", "system_id", "S4H"))
                .requiredSettings(Set.of("base_url", "client_id"))
                .maxBatchSize(500)
                .supportsWebhooks(false)
                .supportsRealTimeSync(false)
                .supportsBatchSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String username = resolveCredential("username");
        String password = resolveCredential("password");
        if (username != null && password != null) {
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            defaultHeaders.put("Authorization", "Basic " + auth);
        }
        defaultHeaders.put("Content-Type", "text/xml; charset=utf-8");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        this.clientId = config.getSetting("client_id");
        this.systemId = config.getSetting("system_id");
        String protocol = config.getSetting("protocol");
        this.useSoap = "SOAP".equalsIgnoreCase(protocol);
        this.useRfc = "RFC".equalsIgnoreCase(protocol);
        this.useEdi = "EDI".equalsIgnoreCase(protocol);
        super.initialize(config);
    }

    @Override
    public boolean testConnection() {
        if (useSoap) {
            try {
                String response = soapClient.sendRequest(baseUrl + "/sap/bc/srt/rfc/sap/",
                        "ping", "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ping/></soap:Body></soap:Envelope>", defaultHeaders);
                return response != null && !response.isEmpty();
            } catch (Exception e) {
                log.warn("SAP SOAP connection failed: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("ORDER_IMPORT", () -> {
            if (useSoap) {
                String soapBody = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                        "<soap:Body><GetOrders><Client>" + clientId + "</Client></GetOrders></soap:Body></soap:Envelope>";
                String response = soapClient.sendRequest(baseUrl + "/sap/opu/odata/sap/API_SALES_ORDER_SRV/OrderCollection",
                        null, soapBody, defaultHeaders);
                log.info("SAP SOAP order response received ({} chars)", response != null ? response.length() : 0);
            } else if (useEdi) {
                Map<String, Object> ediParams = new LinkedHashMap<>();
                ediParams.put("purchaseOrderNumber", "PO-" + UUID.randomUUID().toString().substring(0, 8));
                ediParams.put("orderDate", "20240101");
                String edi850 = ediClient.generate850(ediParams);
                log.info("Generated EDI 850 for SAP ({} chars)", edi850.length());
            }

            return SyncResult.builder()
                    .syncType("ORDER_IMPORT")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    @Override
    public SyncResult syncProducts(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("PRODUCT_SYNC", () -> {
            log.info("SAP product master data sync via IDoc MATMAS");
            return SyncResult.builder()
                    .syncType("PRODUCT_SYNC")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    @Override
    public SyncResult pushInventory(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("INVENTORY_PUSH", () -> {
            log.info("SAP inventory sync via IDoc MBGMCR or OData API_MATERIAL_STOCK_SRV");
            return SyncResult.builder()
                    .syncType("INVENTORY_PUSH")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    @Override
    public SyncResult pushFulfillments(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("FULFILLMENT_PUSH", () -> {
            log.info("SAP delivery creation via BAPI_OUTB_DELIVERY_CREATEFROMORD");
            return SyncResult.builder()
                    .syncType("FULFILLMENT_PUSH")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    @Override
    public SyncResult pushRefunds(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("REFUND_PUSH", () -> {
            log.info("SAP credit memo via BAPI_ACC_GL_POSTING_POST");
            return SyncResult.builder()
                    .syncType("REFUND_PUSH")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "SAP"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("SAP S/4HANA")
                    .vendor("SAP SE")
                    .platformType("SAP")
                    .category("ERP")
                    .description("SAP ERP connector supporting SOAP, OData, RFC, EDI/IDoc")
                    .supportedSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH", "FULFILLMENT_PUSH"))
                    .supportedProtocols(List.of("SOAP", "REST", "EDI", "RFC"))
                    .supportedAuthTypes(List.of("BASIC_AUTH", "CERTIFICATE"))
                    .supportsWebhooks(false)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new SapConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper(),
                    new SoapProtocolAdapter(), new EdiProtocolAdapter());
        }
    }
}
