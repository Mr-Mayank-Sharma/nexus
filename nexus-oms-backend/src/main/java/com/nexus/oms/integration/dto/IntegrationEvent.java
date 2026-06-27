package com.nexus.oms.integration.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IntegrationEvent {

    private String eventId;
    private String eventType;
    private String source;
    private String sourceConnectorId;
    private String tenantId;
    private Instant timestamp;
    private Map<String, Object> payload;
    private Map<String, String> headers;

    private static final ObjectMapper OM = new ObjectMapper();

    private IntegrationEvent() {}

    public static Builder builder() { return new Builder(); }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getSource() { return source; }
    public String getSourceConnectorId() { return sourceConnectorId; }
    public String getTenantId() { return tenantId; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getPayload() { return payload; }
    public Map<String, String> getHeaders() { return headers; }

    @SuppressWarnings("unchecked")
    public <T> T getPayloadField(String key) {
        Object val = payload != null ? payload.get(key) : null;
        return val != null ? (T) val : null;
    }

    public String toJson() {
        try { return OM.writeValueAsString(this); } catch (JsonProcessingException e) { return "{}"; }
    }

    public static IntegrationEvent fromJson(String json) {
        try { return OM.readValue(json, IntegrationEvent.class); } catch (Exception e) { return null; }
    }

    public static class Builder {
        private final IntegrationEvent event = new IntegrationEvent();
        public Builder eventId(String v) { event.eventId = v; return this; }
        public Builder eventType(String v) { event.eventType = v; return this; }
        public Builder source(String v) { event.source = v; return this; }
        public Builder sourceConnectorId(String v) { event.sourceConnectorId = v; return this; }
        public Builder tenantId(String v) { event.tenantId = v; return this; }
        public Builder timestamp(Instant v) { event.timestamp = v; return this; }
        public Builder payload(Map<String, Object> v) { event.payload = v; return this; }
        public Builder putPayload(String k, Object v) { if (event.payload == null) event.payload = new HashMap<>(); event.payload.put(k, v); return this; }
        public Builder headers(Map<String, String> v) { event.headers = v; return this; }
        public IntegrationEvent build() {
            if (event.eventId == null) event.eventId = UUID.randomUUID().toString();
            if (event.timestamp == null) event.timestamp = Instant.now();
            return event;
        }
    }
}
