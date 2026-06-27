package com.nexus.oms.integration.core;

public interface ConnectorFactory {
    String getPlatformType();
    ConnectorMetadata getMetadata();
    Connector create(CredentialVault credentialVault);
}
