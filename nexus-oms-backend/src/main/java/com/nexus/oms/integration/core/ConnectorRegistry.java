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

    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();
    private final Map<String, ConnectorFactory> factories = new ConcurrentHashMap<>();
    private final CredentialVault credentialVault;

    public ConnectorRegistry(CredentialVault credentialVault) {
        this.credentialVault = credentialVault;
    }

    public void registerFactory(String platformType, ConnectorFactory factory) {
        factories.put(platformType.toUpperCase(), factory);
        log.info("Registered connector factory: {}", platformType);
    }

    public Connector createAndInitialize(ConnectorConfig config) {
        String platform = config.getPlatformType().toUpperCase();
        ConnectorFactory factory = factories.get(platform);
        if (factory == null) {
            throw new IllegalArgumentException("No connector factory for platform: " + platform);
        }

        Connector connector = factory.create(credentialVault);
        connector.initialize(config);

        String id = config.getId() != null ? config.getId() : UUID.randomUUID().toString();
        connectors.put(id, connector);
        log.info("Initialized connector: {} ({})", id, platform);
        return connector;
    }

    public Connector getConnector(String id) {
        Connector c = connectors.get(id);
        if (c == null) throw new IllegalArgumentException("Connector not found: " + id);
        return c;
    }

    public void removeConnector(String id) {
        Connector c = connectors.get(id);
        if (c != null) {
            c.shutdown();
            connectors.remove(id);
        }
    }

    public List<Connector> getAllConnectors() {
        return List.copyOf(connectors.values());
    }

    public List<Connector> getConnectorsByPlatform(String platform) {
        return connectors.values().stream()
                .filter(c -> c.getMetadata().getPlatformType().equalsIgnoreCase(platform))
                .collect(Collectors.toList());
    }

    public List<ConnectorMetadata> getAvailablePlatforms() {
        return factories.values().stream()
                .map(ConnectorFactory::getMetadata)
                .collect(Collectors.toList());
    }

    public boolean hasConnector(String id) {
        return connectors.containsKey(id);
    }

    public int getConnectorCount() { return connectors.size(); }
    public Set<String> getRegisteredPlatforms() { return factories.keySet(); }

    public void shutdownAll() {
        connectors.values().forEach(Connector::shutdown);
        connectors.clear();
        log.info("All connectors shut down");
    }
}
