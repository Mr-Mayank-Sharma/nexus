package com.nexus.oms.integration.connector;

import com.nexus.oms.integration.core.*;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * What a connector advertises must be true. These guards exist because the framework once listed inventory,
 * fulfilment and refund pushes, webhooks and "connected" for platforms where the code logged a sentence and
 * answered COMPLETED with zero items.
 */
class ConnectorTruthTest {

    private static final Path CONNECTORS = Paths.get("src/main/java/com/nexus/oms/integration/connector");

    private ConnectorRegistry registry() {
        ConnectorRegistry registry = new ConnectorRegistry(mock(CredentialVault.class));
        new ConnectorFactoryConfiguration(registry, mock(CredentialVault.class), mock(RestProtocolAdapter.class),
                mock(GraphqlProtocolAdapter.class), new DataMapper()).registerFactories();
        return registry;
    }

    @Test
    void everyAdvertisedSyncTypeIsOneTheDispatcherCanRun() {
        for (ConnectorMetadata m : registry().getAvailablePlatforms()) {
            for (String t : m.getSupportedSyncTypes())
                assertTrue(AbstractConnector.DISPATCHABLE_SYNC_TYPES.contains(t), m.getPlatformType() + " lists " + t + " but executeSync cannot run it");
            for (String t : m.getPreviewSyncTypes())
                assertTrue(AbstractConnector.DISPATCHABLE_SYNC_TYPES.contains(t), m.getPlatformType() + " previews " + t + " but executeSync cannot run it");
        }
    }

    @Test
    void noPlatformClaimsAutomaticWebhooks_becauseNoneIsRegisteredAutomatically() {
        for (ConnectorMetadata m : registry().getAvailablePlatforms())
            assertFalse(m.isSupportsWebhooks(), m.getPlatformType() + " claims webhook support");
    }

    @Test
    void genericPlatformsOnlyPreview() {
        Set<String> dedicated = Set.of("SHOPIFY", "MAGENTO", "AMAZON", "BIGCOMMERCE", "SAP", "SALESFORCE", "FEDEX", "STRIPE",
                "QUICKBOOKS", "TWILIO", "OKTA", "OPENAI");
        for (ConnectorMetadata m : registry().getAvailablePlatforms()) {
            if (dedicated.contains(m.getPlatformType())) continue;
            assertTrue(m.getSupportedSyncTypes().isEmpty(), m.getPlatformType() + " is generic and cannot change anything, yet lists " + m.getSupportedSyncTypes());
            assertNull(m.getDocsUrl(), m.getPlatformType() + " has an invented documentation link");
        }
    }

    @Test
    void sapAdvertisesNothingItDoesNotDo() {
        ConnectorMetadata sap = registry().getAvailablePlatforms().stream().filter(m -> m.getPlatformType().equals("SAP")).findFirst().orElseThrow();
        assertTrue(sap.getSupportedSyncTypes().isEmpty());
        assertTrue(sap.getPreviewSyncTypes().isEmpty());
    }

    @Test
    void noConnectorAnswersCompletedWithNothingDone() throws IOException {
        Pattern emptySuccess = Pattern.compile("Status\\.COMPLETED\\)\\s*\\.itemsSucceeded\\(0\\)");
        Pattern bareSuccess = Pattern.compile("Status\\.COMPLETED\\)\\.build\\(\\)");
        try (Stream<Path> files = Files.walk(CONNECTORS)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                assertFalse(emptySuccess.matcher(src).find(), p + " answers COMPLETED with zero items; use notSupported(...)");
                assertFalse(bareSuccess.matcher(src).find(), p + " answers COMPLETED without doing anything");
            }
        }
    }

    @Test
    void noConnectorRegistersWebhooksAgainstItsOwnConfiguredHost() throws IOException {
        // The parameter used to be called baseUrl, shadowing the platform address, so "registration" posted to Nexus itself.
        Pattern shadow = Pattern.compile("registerWebhooks\\(String baseUrl\\)");
        try (Stream<Path> files = Files.walk(CONNECTORS)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList())
                assertFalse(shadow.matcher(Files.readString(p)).find(), p + " shadows the platform baseUrl in registerWebhooks");
        }
    }

    @Test
    void anUnregisteredWebhookRequestIsRefusedNotLogged() {
        GenericHttpConnector c = new GenericHttpConnector(mock(CredentialVault.class), mock(RestProtocolAdapter.class),
                mock(GraphqlProtocolAdapter.class), new DataMapper(), "UPS", "UPS", "UPS", "SHIPPING", "carrier", null);
        assertThrows(UnsupportedOperationException.class, () -> c.registerWebhooks("https://nexus.example.test"));
    }
}
