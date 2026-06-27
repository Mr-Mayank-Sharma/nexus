package com.nexus.oms.integration.core;

import java.time.LocalDateTime;

public class ConnectorHealth {

    public enum Status { UP, DOWN, DEGRADED, UNKNOWN }

    private final String connectorId;
    private final Status status;
    private final String message;
    private final long latencyMs;
    private final LocalDateTime lastCheckedAt;
    private final LocalDateTime lastSuccessAt;
    private final LocalDateTime lastErrorAt;
    private final int consecutiveFailures;
    private final boolean webhooksRegistered;

    private ConnectorHealth(Builder b) {
        this.connectorId = b.connectorId;
        this.status = b.status;
        this.message = b.message;
        this.latencyMs = b.latencyMs;
        this.lastCheckedAt = b.lastCheckedAt;
        this.lastSuccessAt = b.lastSuccessAt;
        this.lastErrorAt = b.lastErrorAt;
        this.consecutiveFailures = b.consecutiveFailures;
        this.webhooksRegistered = b.webhooksRegistered;
    }

    public static Builder builder() { return new Builder(); }

    public String getConnectorId() { return connectorId; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public long getLatencyMs() { return latencyMs; }
    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public LocalDateTime getLastSuccessAt() { return lastSuccessAt; }
    public LocalDateTime getLastErrorAt() { return lastErrorAt; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public boolean isWebhooksRegistered() { return webhooksRegistered; }

    public static class Builder {
        private String connectorId;
        private Status status = Status.UNKNOWN;
        private String message;
        private long latencyMs;
        private LocalDateTime lastCheckedAt = LocalDateTime.now();
        private LocalDateTime lastSuccessAt;
        private LocalDateTime lastErrorAt;
        private int consecutiveFailures;
        private boolean webhooksRegistered;
        public Builder connectorId(String v) { this.connectorId = v; return this; }
        public Builder status(Status v) { this.status = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder latencyMs(long v) { this.latencyMs = v; return this; }
        public Builder lastCheckedAt(LocalDateTime v) { this.lastCheckedAt = v; return this; }
        public Builder lastSuccessAt(LocalDateTime v) { this.lastSuccessAt = v; return this; }
        public Builder lastErrorAt(LocalDateTime v) { this.lastErrorAt = v; return this; }
        public Builder consecutiveFailures(int v) { this.consecutiveFailures = v; return this; }
        public Builder webhooksRegistered(boolean v) { this.webhooksRegistered = v; return this; }
        public ConnectorHealth build() { return new ConnectorHealth(this); }
    }
}
