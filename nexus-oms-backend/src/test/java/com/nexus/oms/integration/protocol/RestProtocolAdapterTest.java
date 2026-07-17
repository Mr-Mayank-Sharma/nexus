package com.nexus.oms.integration.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestProtocolAdapterTest {

    private RestProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RestProtocolAdapter();
    }

    @Test
    void fallbackGet_returnsErrorNode() {
        JsonNode result = adapter.fallbackGet("http://test.com", "/api", Map.of(), Map.of(), new RuntimeException("timeout"));
        assertNotNull(result);
        assertTrue(result.has("error"));
        assertTrue(result.has("fallback"));
        assertTrue(result.get("fallback").asBoolean());
    }

    @Test
    void fallbackPost_returnsErrorNode() {
        JsonNode result = adapter.fallbackPost("http://test.com", "/api", Map.of(), null, new RuntimeException("timeout"));
        assertNotNull(result);
        assertTrue(result.get("fallback").asBoolean());
    }

    @Test
    void fallbackPut_returnsErrorNode() {
        JsonNode result = adapter.fallbackPut("http://test.com", "/api", Map.of(), null, new RuntimeException("timeout"));
        assertNotNull(result);
        assertTrue(result.get("fallback").asBoolean());
    }

    @Test
    void fallbackDelete_returnsErrorNode() {
        JsonNode result = adapter.fallbackDelete("http://test.com", "/api", Map.of(), new RuntimeException("timeout"));
        assertNotNull(result);
        assertTrue(result.get("fallback").asBoolean());
    }
}
