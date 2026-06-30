package com.nexus.oms.integration.core;

import com.nexus.oms.integration.connector.GenericHttpConnector;
import com.nexus.oms.integration.connector.accounting.QuickBooksConnector;
import com.nexus.oms.integration.connector.ai.OpenAiConnector;
import com.nexus.oms.integration.connector.communication.TwilioConnector;
import com.nexus.oms.integration.connector.crm.SalesforceConnector;
import com.nexus.oms.integration.connector.ecommerce.*;
import com.nexus.oms.integration.connector.erp.SapConnector;
import com.nexus.oms.integration.connector.identity.OktaConnector;
import com.nexus.oms.integration.connector.payment.StripeConnector;
import com.nexus.oms.integration.connector.shipping.FedExConnector;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import java.util.List;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConnectorFactoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConnectorFactoryConfiguration.class);

    private final ConnectorRegistry registry;
    private final CredentialVault credentialVault;
    private final RestProtocolAdapter restClient;
    private final GraphqlProtocolAdapter graphqlClient;
    private final DataMapper dataMapper;

    public ConnectorFactoryConfiguration(ConnectorRegistry registry, CredentialVault credentialVault,
                                          RestProtocolAdapter restClient, GraphqlProtocolAdapter graphqlClient,
                                          DataMapper dataMapper) {
        this.registry = registry;
        this.credentialVault = credentialVault;
        this.restClient = restClient;
        this.graphqlClient = graphqlClient;
        this.dataMapper = dataMapper;
    }

    @PostConstruct
    public void registerFactories() {
        // E-Commerce
        registry.registerFactory("SHOPIFY", new ShopifyConnector.Factory());
        registry.registerFactory("MAGENTO", new MagentoConnector.Factory());
        registry.registerFactory("AMAZON", new AmazonConnector.Factory());
        registry.registerFactory("BIGCOMMERCE", new ShopifyConnector.Factory());

        // Marketplaces
        registry.registerFactory("EBAY", createGenericFactory("EBAY", "eBay", "eBay Inc.", "Marketplace",
                "eBay REST API for order and product management"));
        registry.registerFactory("WALMART", createGenericFactory("WALMART", "Walmart Marketplace", "Walmart", "Marketplace",
                "Walmart Marketplace API connector"));
        registry.registerFactory("ETSY", createGenericFactory("ETSY", "Etsy", "Etsy Inc.", "Marketplace",
                "Etsy REST API for shop and order management"));
        registry.registerFactory("ALIBABA", createGenericFactory("ALIBABA", "Alibaba.com", "Alibaba Group", "Marketplace",
                "Alibaba.com REST API for trade management"));

        // ERP
        registry.registerFactory("SAP", new SapConnector.Factory());
        registry.registerFactory("NETSUITE", createGenericFactory("NETSUITE", "NetSuite", "Oracle", "ERP",
                "Oracle NetSuite SuiteTalk REST API"));
        registry.registerFactory("ORACLE_ERP", createGenericFactory("ORACLE_ERP", "Oracle ERP Cloud", "Oracle", "ERP",
                "Oracle ERP Cloud REST API"));
        registry.registerFactory("DYNAMICS_365", createGenericFactory("DYNAMICS_365", "Microsoft Dynamics 365", "Microsoft", "ERP",
                "Microsoft Dynamics 365 F&O REST API"));

        // CRM
        registry.registerFactory("SALESFORCE", new SalesforceConnector.Factory());
        registry.registerFactory("HUBSPOT", createGenericFactory("HUBSPOT", "HubSpot", "HubSpot Inc.", "CRM",
                "HubSpot CRM API for contacts, deals, and tickets"));
        registry.registerFactory("ZOHO_CRM", createGenericFactory("ZOHO_CRM", "Zoho CRM", "Zoho Corp.", "CRM",
                "Zoho CRM REST API"));

        // WMS
        registry.registerFactory("MANHATTAN", createGenericFactory("MANHATTAN", "Manhattan Associates", "Manhattan Associates", "WMS",
                "Manhattan Scale WMS API"));
        registry.registerFactory("BLUE_YONDER", createGenericFactory("BLUE_YONDER", "Blue Yonder", "Blue Yonder", "WMS",
                "Blue Yonder WMS API (formerly JDA)"));
        registry.registerFactory("ORACLE_WMS", createGenericFactory("ORACLE_WMS", "Oracle WMS Cloud", "Oracle", "WMS",
                "Oracle WMS Cloud REST API"));

        // TMS
        registry.registerFactory("PROJECT44", createGenericFactory("PROJECT44", "project44", "project44", "TMS",
                "project44 visibility API"));
        registry.registerFactory("FOURKITES", createGenericFactory("FOURKITES", "FourKites", "FourKites Inc.", "TMS",
                "FourKites real-time tracking API"));

        // Shipping
        registry.registerFactory("FEDEX", new FedExConnector.Factory());
        registry.registerFactory("UPS", createGenericFactory("UPS", "UPS", "United Parcel Service", "Shipping",
                "UPS REST API for rates, shipping, and tracking"));
        registry.registerFactory("DHL", createGenericFactory("DHL", "DHL Express", "DHL Group", "Shipping",
                "DHL REST API for shipping and tracking"));

        // Payments
        registry.registerFactory("STRIPE", new StripeConnector.Factory());
        registry.registerFactory("PAYPAL", createGenericFactory("PAYPAL", "PayPal", "PayPal Holdings", "Payments",
                "PayPal REST API for payments and refunds"));
        registry.registerFactory("ADYEN", createGenericFactory("ADYEN", "Adyen", "Adyen N.V.", "Payments",
                "Adyen API for payment processing"));

        // Accounting
        registry.registerFactory("QUICKBOOKS", new QuickBooksConnector.Factory());
        registry.registerFactory("XERO", createGenericFactory("XERO", "Xero", "Xero Limited", "Accounting",
                "Xero accounting API"));
        registry.registerFactory("SAGE", createGenericFactory("SAGE", "Sage", "Sage Group", "Accounting",
                "Sage accounting API"));

        // POS
        registry.registerFactory("SQUARE", createGenericFactory("SQUARE", "Square", "Block Inc.", "POS",
                "Square REST API for payments and orders"));
        registry.registerFactory("LIGHTSPEED", createGenericFactory("LIGHTSPEED", "Lightspeed", "Lightspeed Commerce", "POS",
                "Lightspeed REST API"));

        // Communication
        registry.registerFactory("TWILIO", new TwilioConnector.Factory());
        registry.registerFactory("SENDGRID", createGenericFactory("SENDGRID", "SendGrid", "Twilio Inc.", "Communication",
                "SendGrid email API"));
        registry.registerFactory("SLACK", createGenericFactory("SLACK", "Slack", "Salesforce Inc.", "Communication",
                "Slack Web API for notifications"));

        // Identity & SSO
        registry.registerFactory("OKTA", new OktaConnector.Factory());
        registry.registerFactory("AUTH0", createGenericFactory("AUTH0", "Auth0", "Okta Inc.", "Identity & SSO",
                "Auth0 Management API"));

        // Analytics
        registry.registerFactory("POWER_BI", createGenericFactory("POWER_BI", "Microsoft Power BI", "Microsoft", "Analytics",
                "Power BI REST API for dataset push"));
        registry.registerFactory("TABLEAU", createGenericFactory("TABLEAU", "Tableau", "Salesforce Inc.", "Analytics",
                "Tableau REST API"));
        registry.registerFactory("LOOKER", createGenericFactory("LOOKER", "Looker", "Google", "Analytics",
                "Looker API 4.0"));

        // AI Platforms
        registry.registerFactory("OPENAI", new OpenAiConnector.Factory());
        registry.registerFactory("ANTHROPIC", createGenericFactory("ANTHROPIC", "Anthropic Claude", "Anthropic", "AI Platform",
                "Anthropic Claude API for LLM inference"));
        registry.registerFactory("AWS_BEDROCK", createGenericFactory("AWS_BEDROCK", "AWS Bedrock", "Amazon", "AI Platform",
                "AWS Bedrock API for foundation models"));

        log.info("Registered {} connector factories", registry.getRegisteredPlatforms().size());
    }

    private ConnectorFactory createGenericFactory(String platformType, String name, String vendor,
                                                    String category, String description) {
        return new ConnectorFactory() {
            @Override
            public String getPlatformType() { return platformType; }

            @Override
            public ConnectorMetadata getMetadata() {
                return ConnectorMetadata.builder()
                        .name(name).vendor(vendor).platformType(platformType)
                        .category(category).description(description)
                        .supportedSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH"))
                        .supportedAuthTypes(List.of("API_KEY", "OAUTH2"))
                        .supportsWebhooks(true)
                        .build();
            }

            @Override
            public Connector create(CredentialVault vault) {
                return new GenericHttpConnector(vault, restClient, graphqlClient, dataMapper,
                        platformType, name, vendor, category, description, "https://docs.example.com/" + platformType.toLowerCase());
            }
        };
    }
}
