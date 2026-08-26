package com.nexus.oms.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1.3 contract tests: the legacy bridge NEVER fabricates predictions.
 * When the Flask backend is unreachable, every call must THROW (the
 * controller turns that into a 503). The old silent fallbacks
 * ("FALLBACK_WH", "STANDARD", empty demand) are gone.
 */
class AiServiceTest {

    // Nothing listens here -> deterministic connection-refused, no network flakiness.
    private static final String DEAD_OPS = "http://localhost:59987";
    private static final String DEAD_INTEL = "http://localhost:59988";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AiService aiService = new AiService(DEAD_OPS, DEAD_INTEL, 1000, registry);

    @Test
    void carrierAi_throws_whenBackendDown() {
        assertThrows(RuntimeException.class, () -> aiService.callCarrierAi(Map.of()));
        assertEquals(1.0, registry.counter("nexus.ai.legacy_bridge.errors", "endpoint", "carrier").count());
    }

    @Test
    void demandAi_throws_whenBackendDown() {
        assertThrows(RuntimeException.class, () -> aiService.callDemandAi(Map.of()));
        assertEquals(1.0, registry.counter("nexus.ai.legacy_bridge.errors", "endpoint", "demand").count());
    }

    @Test
    void inventoryAi_throws_whenBackendDown() {
        assertThrows(RuntimeException.class, () -> aiService.callInventoryAi(Map.of()));
        assertEquals(1.0, registry.counter("nexus.ai.legacy_bridge.errors", "endpoint", "inventory").count());
    }
}
