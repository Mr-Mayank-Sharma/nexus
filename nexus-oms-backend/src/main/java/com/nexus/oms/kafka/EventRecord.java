package com.nexus.oms.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventRecord(
        String eventId,
        String eventType,
        String source,
        Instant timestamp,
        String data,
        UUID tenantId
) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static EventRecord create(String eventType, String source, String data, UUID tenantId) {
        return new EventRecord(
                UUID.randomUUID().toString(),
                eventType,
                source,
                Instant.now(),
                data,
                tenantId
        );
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize EventRecord", e);
        }
    }

    public static EventRecord fromJson(String json) {
        try {
            return MAPPER.readValue(json, EventRecord.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize EventRecord", e);
        }
    }
}
