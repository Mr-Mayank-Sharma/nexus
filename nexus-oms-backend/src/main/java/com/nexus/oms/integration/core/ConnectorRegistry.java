package com.nexus.oms.integration.core;

import com.nexus.oms.integration.dto.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final Map<String, ConnectorFactory> factories = new ConcurrentHashMap<>();
    private final CredentialVault credentialVault;

    public ConnectorRegistry(CredentialVault credentialVault) {
        this.credentialVault = credentialVault;
    }

    public void registerFactory(String platformType, ConnectorFactory factory) {
        factories.put(platformType.toUpperCase(), factory);
        log.info("Registered connector factory: {}", platformType);
    }

    /**
     * Builds a connector for one stored connection. The registry keeps no connections of its own: they live
     * in the integration store tables (see StoreConnectorService), which are tenant-scoped and persistent.
     */
    public Connector initialize(ConnectorConfig config) {
        String platform = config.getPlatformType().toUpperCase();
        ConnectorFactory factory = factories.get(platform);
        if (factory == null) {
            throw new IllegalArgumentException("No connector factory for platform: " + platform);
        }
        Connector connector = factory.create(credentialVault);
        connector.initialize(config);
        return connector;
    }

    public List<ConnectorMetadata> getAvailablePlatforms() {
        return factories.values().stream()
                .map(ConnectorFactory::getMetadata)
                .collect(Collectors.toList());
    }


    public Set<String> getRegisteredPlatforms() { return factories.keySet(); }

}
