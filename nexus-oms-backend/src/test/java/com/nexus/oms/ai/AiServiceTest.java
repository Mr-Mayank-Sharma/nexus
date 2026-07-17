package com.nexus.oms.ai;

import com.nexus.oms.dto.AllocationResponse;
import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.dto.InventoryRecommendation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {

    private final AiService aiService = new AiService("http://localhost:8000", "http://localhost:8001");

    @Test
    void fallbackRouting_returnsDefaultResponse() {
        AllocationResponse result = aiService.fallbackRouting(Map.of(), new RuntimeException("timeout"));
        assertNotNull(result);
        assertEquals("FALLBACK_WH", result.getWarehouse());
        assertEquals("FALLBACK_CARRIER", result.getCarrier());
        assertEquals("FALLBACK", result.getRule());
    }

    @Test
    void fallbackDemand_returnsEmptyResponse() {
        DemandForecastResponse result = aiService.fallbackDemand(Map.of(), new RuntimeException("timeout"));
        assertNotNull(result);
        assertNotNull(result.getNext7Days());
        assertNotNull(result.getNext30Days());
        assertNotNull(result.getConfidence());
    }

    @Test
    void fallbackInventory_returnsSafeDefaults() {
        InventoryRecommendation result = aiService.fallbackInventory(Map.of(), new RuntimeException("timeout"));
        assertNotNull(result);
        assertFalse(result.isNeedsReorder());
        assertEquals(0, result.getRecommendedQty());
        assertEquals(0.0, result.getConfidence());
    }
}
