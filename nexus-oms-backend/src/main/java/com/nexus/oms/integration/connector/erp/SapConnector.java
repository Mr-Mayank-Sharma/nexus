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
                .supportedSyncTypes(List.of())
                .previewSyncTypes(List.of())
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

    /**
     * Nothing beyond a reachability probe is built for SAP. The SOAP probe only shows the gateway answers; it does
     * not prove the credentials or the client number, and the OData, IDoc, RFC and EDI paths have no probe at all,
     * so they report failure rather than a connection nobody checked.
     */
    @Override
    public boolean testConnection() {
        if (!useSoap) {
            log.warn("SAP connection test is only implemented for the SOAP protocol");
            return false;
        }
        try {
            String response = soapClient.sendRequest(baseUrl + "/sap/bc/srt/rfc/sap/",
                    "ping", "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ping/></soap:Body></soap:Envelope>", defaultHeaders);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.warn("SAP SOAP connection failed: {}", e.getMessage());
            return false;
        }
    }

    // ORDER_IMPORT, PRODUCT_SYNC, INVENTORY_PUSH, FULFILLMENT_PUSH and REFUND_PUSH are inherited from the base
    // class and answer NOT_SUPPORTED. They used to log "SAP delivery creation via BAPI..." and report COMPLETED.

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
                    .supportedSyncTypes(List.of())
                .previewSyncTypes(List.of())
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
