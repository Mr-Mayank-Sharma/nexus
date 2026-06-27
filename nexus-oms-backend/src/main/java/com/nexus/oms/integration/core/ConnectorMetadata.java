package com.nexus.oms.integration.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConnectorMetadata {

    private final String name;
    private final String version;
    private final String vendor;
    private final String platformType;
    private final String category;
    private final String description;
    private final String website;
    private final String docsUrl;
    private final List<String> supportedSyncTypes;
    private final List<String> supportedProtocols;
    private final List<String> supportedAuthTypes;
    private final Map<String, String> defaultSettings;
    private final Set<String> requiredSettings;
    private final int maxBatchSize;
    private final boolean supportsWebhooks;
    private final boolean supportsRealTimeSync;
    private final boolean supportsBatchSync;

    public ConnectorMetadata(Builder builder) {
        this.name = builder.name;
        this.version = builder.version;
        this.vendor = builder.vendor;
        this.platformType = builder.platformType;
        this.category = builder.category;
        this.description = builder.description;
        this.website = builder.website;
        this.docsUrl = builder.docsUrl;
        this.supportedSyncTypes = List.copyOf(builder.supportedSyncTypes);
        this.supportedProtocols = List.copyOf(builder.supportedProtocols);
        this.supportedAuthTypes = List.copyOf(builder.supportedAuthTypes);
        this.defaultSettings = Map.copyOf(builder.defaultSettings);
        this.requiredSettings = Set.copyOf(builder.requiredSettings);
        this.maxBatchSize = builder.maxBatchSize;
        this.supportsWebhooks = builder.supportsWebhooks;
        this.supportsRealTimeSync = builder.supportsRealTimeSync;
        this.supportsBatchSync = builder.supportsBatchSync;
    }

    public static Builder builder() { return new Builder(); }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getVendor() { return vendor; }
    public String getPlatformType() { return platformType; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getWebsite() { return website; }
    public String getDocsUrl() { return docsUrl; }
    public List<String> getSupportedSyncTypes() { return supportedSyncTypes; }
    public List<String> getSupportedProtocols() { return supportedProtocols; }
    public List<String> getSupportedAuthTypes() { return supportedAuthTypes; }
    public Map<String, String> getDefaultSettings() { return defaultSettings; }
    public Set<String> getRequiredSettings() { return requiredSettings; }
    public int getMaxBatchSize() { return maxBatchSize; }
    public boolean isSupportsWebhooks() { return supportsWebhooks; }
    public boolean isSupportsRealTimeSync() { return supportsRealTimeSync; }
    public boolean isSupportsBatchSync() { return supportsBatchSync; }

    public static class Builder {
        private String name;
        private String version = "1.0.0";
        private String vendor;
        private String platformType;
        private String category;
        private String description;
        private String website;
        private String docsUrl;
        private List<String> supportedSyncTypes = List.of();
        private List<String> supportedProtocols = List.of("REST");
        private List<String> supportedAuthTypes = List.of("API_KEY");
        private Map<String, String> defaultSettings = Map.of();
        private Set<String> requiredSettings = Set.of();
        private int maxBatchSize = 100;
        private boolean supportsWebhooks;
        private boolean supportsRealTimeSync = true;
        private boolean supportsBatchSync;

        public Builder name(String n) { this.name = n; return this; }
        public Builder version(String v) { this.version = v; return this; }
        public Builder vendor(String v) { this.vendor = v; return this; }
        public Builder platformType(String t) { this.platformType = t; return this; }
        public Builder category(String c) { this.category = c; return this; }
        public Builder description(String d) { this.description = d; return this; }
        public Builder website(String w) { this.website = w; return this; }
        public Builder docsUrl(String d) { this.docsUrl = d; return this; }
        public Builder supportedSyncTypes(List<String> s) { this.supportedSyncTypes = s; return this; }
        public Builder supportedProtocols(List<String> s) { this.supportedProtocols = s; return this; }
        public Builder supportedAuthTypes(List<String> s) { this.supportedAuthTypes = s; return this; }
        public Builder defaultSettings(Map<String, String> m) { this.defaultSettings = m; return this; }
        public Builder requiredSettings(Set<String> s) { this.requiredSettings = s; return this; }
        public Builder maxBatchSize(int m) { this.maxBatchSize = m; return this; }
        public Builder supportsWebhooks(boolean s) { this.supportsWebhooks = s; return this; }
        public Builder supportsRealTimeSync(boolean s) { this.supportsRealTimeSync = s; return this; }
        public Builder supportsBatchSync(boolean s) { this.supportsBatchSync = s; return this; }
        public ConnectorMetadata build() { return new ConnectorMetadata(this); }
    }
}
